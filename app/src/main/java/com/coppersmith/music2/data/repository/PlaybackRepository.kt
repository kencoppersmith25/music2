package com.coppersmith.music2.data.repository

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.coppersmith.music2.data.HardcodedDataSource
import com.coppersmith.music2.data.model.Category
import com.coppersmith.music2.data.model.PlayerState
import com.coppersmith.music2.data.model.Station
import com.coppersmith.music2.diagnostics.RideLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.coppersmith.music2.data.model.TrackInfoResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object PlaybackRepository {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val playlist = mutableListOf<Category>()

    // Expose categories cleanly from the data source
    val predefinedCategories = HardcodedDataSource.predefinedCategories

    private var appContext: Context? = null

    init {
        // Default init; MainActivity calls initRepository(context) to restore saved user categories
        scope.launch(Dispatchers.IO) {
            if (playlist.isEmpty()) {
                playlist.add(Category(id = "cat_mixed", name = "Mixed", stations = HardcodedDataSource.initialMixedStations))
                commitState(0, 0)
            }
        }
    }

    fun initRepository(context: Context) {
        appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            val loaded = loadPlaylistFromDisk(context.applicationContext)
            mutex.withLock {
                if (loaded.isNotEmpty()) {
                    playlist.clear()
                    playlist.addAll(loaded)
                } else if (playlist.isEmpty()) {
                    playlist.add(Category(id = "cat_mixed", name = "Mixed", stations = HardcodedDataSource.initialMixedStations))
                    savePlaylistToDisk(context.applicationContext)
                }
                commitState(0, 0)
            }
        }
    }

    private fun savePlaylistToDisk(context: Context? = null) {
        val ctx = context ?: appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("music2_user_data", Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            for (cat in playlist) {
                val catObj = JSONObject()
                catObj.put("id", cat.id)
                catObj.put("name", cat.name)

                val stationsArr = JSONArray()
                for (stat in cat.stations) {
                    val statObj = JSONObject()
                    statObj.put("id", stat.id)
                    statObj.put("name", stat.name)
                    statObj.put("streamUrl", stat.streamUrl)
                    stationsArr.put(statObj)
                }
                catObj.put("stations", stationsArr)
                jsonArray.put(catObj)
            }
            prefs.edit().putString("saved_playlist_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPlaylistFromDisk(context: Context): List<Category> {
        val result = mutableListOf<Category>()
        try {
            val prefs = context.getSharedPreferences("music2_user_data", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("saved_playlist_json", null)
            if (!jsonStr.isNullOrBlank()) {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val catObj = jsonArray.getJSONObject(i)
                    val catId = catObj.optString("id", "cat_$i")
                    val catName = catObj.optString("name", "Category $i")
                    val stationsArr = catObj.optJSONArray("stations") ?: JSONArray()

                    val stationsList = mutableListOf<Station>()
                    for (j in 0 until stationsArr.length()) {
                        val statObj = stationsArr.getJSONObject(j)
                        val statId = statObj.optString("id", "stat_${i}_$j")
                        val statName = statObj.optString("name", "Station $j")
                        val streamUrl = statObj.optString("streamUrl", "")
                        if (streamUrl.isNotBlank()) {
                            stationsList.add(Station(id = statId, name = statName, streamUrl = streamUrl))
                        }
                    }
                    if (stationsList.isNotEmpty()) {
                        result.add(Category(id = catId, name = catName, stations = stationsList))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun initPlayer(context: Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        }
    }

    fun setLoading(loading: Boolean) {
        _state.update { it.copy(isLoading = loading) }
    }

    fun setPlaying(playing: Boolean) {
        _state.update { it.copy(isPlaying = playing) }
    }

    fun updateMetadata(title: String?, artist: String?) {
        _state.update {
            it.copy(
                trackTitle = title?.trim()?.ifBlank { null },
                artistName = artist?.trim()?.ifBlank { null }
            )
        }
    }

    fun toggleContinuousNav() {
        _state.update { it.copy(isContinuousNav = !it.isContinuousNav) }
    }

    fun toggleAnnounceCategories() {
        _state.update { it.copy(announceCategories = !it.announceCategories) }
    }

    fun setBluetoothBackAction(action: String) {
        _state.update { it.copy(bluetoothBackAction = action) }
    }

    fun setSearchResultLimit(limit: Int) {
        val safeLimit = limit.coerceIn(5, 100)
        _state.update { it.copy(searchResultLimit = safeLimit) }
    }

    fun toggleCategorySkipped(categoryIndex: Int) {
        val category = playlist.getOrNull(categoryIndex) ?: return
        _state.update { current ->
            val set = current.skippedCategoryIds.toMutableSet()
            if (set.contains(category.id)) {
                set.remove(category.id)
            } else {
                set.add(category.id)
            }
            current.copy(skippedCategoryIds = set)
        }
    }

    private fun commitState(categoryIndex: Int, stationIndex: Int) {
        if (playlist.isEmpty()) return
        val safeCatIdx = categoryIndex.coerceIn(0, playlist.size - 1)
        val category = playlist[safeCatIdx]
        if (category.stations.isEmpty()) return
        val safeStatIdx = stationIndex.coerceIn(0, category.stations.size - 1)
        val station = category.stations[safeStatIdx]

        _state.update {
            it.copy(
                categoryIndex = safeCatIdx,
                stationIndex = safeStatIdx,
                currentCategory = category,
                currentStation = station,
                isPlaying = true
            )
        }
    }

    suspend fun togglePlayPause() = mutex.withLock {
        val currentState = _state.value
        if (currentState.currentStation == null && playlist.isNotEmpty()) {
            commitState(0, 0)
        } else {
            _state.update { it.copy(isPlaying = !it.isPlaying) }
        }
    }

    suspend fun loadCategoryLive(categoryId: String, categoryName: String, tag: String) = mutex.withLock {
        val displayName = if (categoryName.startsWith("Search:", ignoreCase = true)) categoryName else "Search: ${categoryName.replaceFirstChar { it.uppercase() }}"
        val searchCatId = "cat_search_${tag.lowercase().replace(" ", "_")}"

        val existingIndex = playlist.indexOfFirst {
            it.id == searchCatId || it.name.equals(displayName, ignoreCase = true)
        }
        if (existingIndex != -1 && playlist[existingIndex].stations.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                commitState(existingIndex, 0)
                exoPlayer?.let { player ->
                    player.prepare()
                    player.play()
                }
            }
            return@withLock
        }

        val cleanSearchTerm = categoryName.substringAfter("Search: ").trim().replaceFirstChar { it.uppercase() }
        RideLogger.log("SEARCH_STARTED query='$tag' term='$cleanSearchTerm'")
        setLoading(true)
        _state.update { it.copy(searchingCategoryName = cleanSearchTerm, searchError = null) }
        val stations = mutableListOf<Station>()
        try {
            withContext(Dispatchers.IO) {
                val baseUrls = listOf(
                    "https://de1.api.radio-browser.info",
                    "https://all.api.radio-browser.info",
                    "https://de2.api.radio-browser.info",
                    "https://fi1.api.radio-browser.info",
                    "https://nl1.api.radio-browser.info"
                )

                val queryLower = tag.trim().lowercase()
                val searchTags = mutableListOf(tag)

                // Synergy expansions matching your Music One Chat setup
                when {
                    queryLower.contains("classical") || queryLower.contains("symphony") || queryLower.contains("orchestra") -> {
                        searchTags.addAll(listOf("Classical", "Symphony", "Orchestra", "Opera", "Chamber", "Baroque", "Philharmonic"))
                    }
                    queryLower.contains("jazz") || queryLower.contains("blues") -> {
                        searchTags.addAll(listOf("Jazz", "Blues", "Smooth Jazz", "Bebop"))
                    }
                    queryLower.contains("electronic") || queryLower.contains("techno") || queryLower.contains("house") -> {
                        searchTags.addAll(listOf("Electronic", "Techno", "House", "Trance", "Ambient"))
                    }
                    else -> {}
                }

                val rawStations = mutableListOf<Station>()
                val searchLimit = _state.value.searchResultLimit

                for (baseUrl in baseUrls) {
                    try {
                        for (t in searchTags) {
                            val encodedTag = URLEncoder.encode(t, "UTF-8")
                            val url = URL("$baseUrl/json/stations/search?tag=$encodedTag&limit=$searchLimit&hidebroken=true&order=votes&reverse=true")
                            val connection = url.openConnection() as HttpURLConnection
                            connection.requestMethod = "GET"
                            connection.setRequestProperty("User-Agent", "NoHandsRadio/1.1 Android")
                            connection.connectTimeout = 4000
                            connection.readTimeout = 4000

                            if (connection.responseCode == 200) {
                                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                                val response = reader.readText()
                                reader.close()

                                val jsonArray = JSONArray(response)
                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    val name = obj.optString("name", "Unknown Station").trim()
                                    val streamUrl = obj.optString("url_resolved", "").ifBlank { obj.optString("url", "") }.trim()
                                    val id = obj.optString("stationuuid", i.toString())
                                    if (streamUrl.isNotEmpty() && !streamUrl.contains("localhost")) {
                                        rawStations.add(Station(id = id, name = name, streamUrl = streamUrl))
                                    }
                                }
                            }
                            connection.disconnect()
                        }
                        if (rawStations.isNotEmpty()) break
                    } catch (_: Exception) {
                        // Try next base URL
                    }
                }

                if (rawStations.isEmpty()) {
                    for (baseUrl in baseUrls) {
                        try {
                            val encodedTag = URLEncoder.encode(tag.trim(), "UTF-8")
                            val url = URL("$baseUrl/json/stations/search?name=$encodedTag&limit=$searchLimit&hidebroken=true&order=votes&reverse=true")
                            val connection = url.openConnection() as HttpURLConnection
                            connection.requestMethod = "GET"
                            connection.setRequestProperty("User-Agent", "NoHandsRadio/1.1 Android")
                            connection.connectTimeout = 4000
                            connection.readTimeout = 4000

                            if (connection.responseCode == 200) {
                                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                                val response = reader.readText()
                                reader.close()

                                val jsonArray = JSONArray(response)
                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    val name = obj.optString("name", "Unknown Station").trim()
                                    val streamUrl = obj.optString("url_resolved", "").ifBlank { obj.optString("url", "") }.trim()
                                    val id = obj.optString("stationuuid", i.toString())
                                    if (streamUrl.isNotEmpty() && !streamUrl.contains("localhost")) {
                                        rawStations.add(Station(id = id, name = name, streamUrl = streamUrl))
                                    }
                                }
                            }
                            connection.disconnect()
                            if (rawStations.isNotEmpty()) break
                        } catch (_: Exception) {
                            // Try next base URL
                        }
                    }
                }

                // Deduplicate stations
                stations.addAll(rawStations.distinctBy { it.streamUrl.lowercase().split('?')[0] }.distinctBy { it.name.lowercase() })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            setLoading(false)
        }

        if (stations.isNotEmpty()) {
            RideLogger.log("SEARCH_SUCCESS category='$displayName' stationsCount=${stations.size}")
            val newCategory = Category(id = searchCatId, name = displayName, stations = stations)
            withContext(Dispatchers.Main) {
                val checkIndex = playlist.indexOfFirst {
                    it.id == searchCatId || it.name.equals(displayName, ignoreCase = true)
                }
                val catIdx = if (checkIndex != -1) {
                    playlist[checkIndex] = newCategory
                    checkIndex
                } else {
                    playlist.add(newCategory)
                    playlist.size - 1
                }

                _state.update { it.copy(searchingCategoryName = null, searchError = null) }
                savePlaylistToDisk()
                commitState(catIdx, 0)
                exoPlayer?.let { player ->
                    player.prepare()
                    player.play()
                }
            }
        } else {
            RideLogger.log("SEARCH_NO_RESULTS category='$displayName'")
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        searchingCategoryName = null,
                        searchError = "No stations found for '${categoryName.replace("Search: ", "")}'"
                    )
                }
            }
        }
    }

    fun clearSearchError() {
        _state.update { it.copy(searchError = null) }
    }

    fun toggleStationSkipped(stationId: String) {
        _state.update { current ->
            val set = current.skippedStationIds.toMutableSet()
            if (set.contains(stationId)) {
                set.remove(stationId)
            } else {
                set.add(stationId)
            }
            current.copy(skippedStationIds = set)
        }
    }

    fun nextStation() {
        val currentCat = playlist.getOrNull(_state.value.categoryIndex) ?: return
        if (currentCat.stations.isEmpty()) return

        val isContinuous = _state.value.isContinuousNav
        val currentStatIdx = _state.value.stationIndex
        val currentCatIdx = _state.value.categoryIndex
        val skippedCats = _state.value.skippedCategoryIds
        val skippedStats = _state.value.skippedStationIds

        scope.launch(Dispatchers.Main) {
            mutex.withLock {
                var nextStatIdx = -1
                for (s in (currentStatIdx + 1) until currentCat.stations.size) {
                    if (!skippedStats.contains(currentCat.stations[s].id)) {
                        nextStatIdx = s
                        break
                    }
                }

                if (nextStatIdx != -1) {
                    commitState(currentCatIdx, nextStatIdx)
                } else {
                    if (isContinuous && playlist.size > 1) {
                        var nextCatIdx = currentCatIdx
                        for (i in 1..playlist.size) {
                            val candidate = (currentCatIdx + i) % playlist.size
                            if (!skippedCats.contains(playlist[candidate].id) || i == playlist.size) {
                                nextCatIdx = candidate
                                break
                            }
                        }
                        val targetCat = playlist[nextCatIdx]
                        val firstValidStat = targetCat.stations.indexOfFirst { !skippedStats.contains(it.id) }
                        commitState(nextCatIdx, if (firstValidStat != -1) firstValidStat else 0)
                    } else {
                        val firstValidStat = currentCat.stations.indexOfFirst { !skippedStats.contains(it.id) }
                        commitState(currentCatIdx, if (firstValidStat != -1) firstValidStat else 0)
                    }
                }
            }
        }
    }

    fun previousStation() {
        val currentCat = playlist.getOrNull(_state.value.categoryIndex) ?: return
        if (currentCat.stations.isEmpty()) return

        val isContinuous = _state.value.isContinuousNav
        val currentStatIdx = _state.value.stationIndex
        val currentCatIdx = _state.value.categoryIndex
        val skippedCats = _state.value.skippedCategoryIds
        val skippedStats = _state.value.skippedStationIds

        scope.launch(Dispatchers.Main) {
            mutex.withLock {
                var prevStatIdx = -1
                for (s in (currentStatIdx - 1) downTo 0) {
                    if (!skippedStats.contains(currentCat.stations[s].id)) {
                        prevStatIdx = s
                        break
                    }
                }

                if (prevStatIdx != -1) {
                    commitState(currentCatIdx, prevStatIdx)
                } else {
                    if (isContinuous && playlist.size > 1) {
                        var prevCatIdx = currentCatIdx
                        for (i in 1..playlist.size) {
                            val candidate = if (currentCatIdx - i < 0) (playlist.size + ((currentCatIdx - i) % playlist.size)) % playlist.size else currentCatIdx - i
                            if (!skippedCats.contains(playlist[candidate].id) || i == playlist.size) {
                                prevCatIdx = candidate
                                break
                            }
                        }
                        val prevCat = playlist[prevCatIdx]
                        val lastValidStat = prevCat.stations.indexOfLast { !skippedStats.contains(it.id) }
                        val safeStatIdx = if (lastValidStat != -1) lastValidStat else (prevCat.stations.size - 1).coerceAtLeast(0)
                        commitState(prevCatIdx, safeStatIdx)
                    } else {
                        val lastValidStat = currentCat.stations.indexOfLast { !skippedStats.contains(it.id) }
                        val safeStatIdx = if (lastValidStat != -1) lastValidStat else (currentCat.stations.size - 1).coerceAtLeast(0)
                        commitState(currentCatIdx, safeStatIdx)
                    }
                }
            }
        }
    }

    private val categoryLastStationMap = mutableMapOf<Int, Int>()

    private fun commitState(categoryIndex: Int, stationIndex: Int? = null) {
        if (playlist.isEmpty()) return
        val safeCatIdx = categoryIndex.coerceIn(0, playlist.size - 1)
        val category = playlist[safeCatIdx]
        if (category.stations.isEmpty()) return

        // Save the current station index before leaving the current category
        val currentCatIdx = _state.value.categoryIndex
        if (currentCatIdx in playlist.indices) {
            categoryLastStationMap[currentCatIdx] = _state.value.stationIndex
        }

        // If a stationIndex is provided, use it. Otherwise recall from memory or default to 0.
        val safeStatIdx = if (stationIndex != null) {
            stationIndex.coerceIn(0, category.stations.size - 1)
        } else {
            categoryLastStationMap[safeCatIdx]?.coerceIn(0, category.stations.size - 1) ?: 0
        }

        categoryLastStationMap[safeCatIdx] = safeStatIdx
        val station = category.stations[safeStatIdx]

        RideLogger.log("COMMIT_STATE catIdx=$safeCatIdx statIdx=$safeStatIdx category='${category.name}' station='${station.name}'")

        _state.update {
            it.copy(
                categoryIndex = safeCatIdx,
                stationIndex = safeStatIdx,
                currentCategory = category,
                currentStation = station,
                isPlaying = true,
                trackTitle = null,
                artistName = null
            )
        }
    }

    suspend fun deleteCategory(categoryIndex: Int) = mutex.withLock {
        if (playlist.isEmpty() || categoryIndex !in playlist.indices) return@withLock
        playlist.removeAt(categoryIndex)
        categoryLastStationMap.remove(categoryIndex)
        savePlaylistToDisk()

        if (playlist.isNotEmpty()) {
            val safeCatIdx = categoryIndex.coerceIn(0, playlist.size - 1)
            commitState(safeCatIdx, null)
        } else {
            _state.update {
                PlayerState()
            }
        }
    }

    suspend fun deleteCurrentStation() = mutex.withLock {
        val stateVal = _state.value
        val catIdx = stateVal.categoryIndex
        val statIdx = stateVal.stationIndex
        val category = playlist.getOrNull(catIdx) ?: return@withLock
        if (statIdx !in category.stations.indices) return@withLock

        val mutableStations = category.stations.toMutableList()
        mutableStations.removeAt(statIdx)

        if (mutableStations.isNotEmpty()) {
            playlist[catIdx] = category.copy(stations = mutableStations)
            savePlaylistToDisk()
            val nextStatIdx = statIdx.coerceIn(0, mutableStations.size - 1)
            commitState(catIdx, nextStatIdx)
        } else {
            playlist.removeAt(catIdx)
            categoryLastStationMap.remove(catIdx)
            savePlaylistToDisk()
            if (playlist.isNotEmpty()) {
                val nextCatIdx = catIdx.coerceIn(0, playlist.size - 1)
                commitState(nextCatIdx, 0)
            } else {
                _state.update { PlayerState() }
            }
        }
    }

    suspend fun saveStationToCategory(station: Station, targetCategoryName: String) = mutex.withLock {
        val cleanName = targetCategoryName.trim().ifBlank { "Favorites" }
        val targetIdx = playlist.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }

        if (targetIdx != -1) {
            val cat = playlist[targetIdx]
            if (cat.stations.none { it.id == station.id || it.streamUrl == station.streamUrl }) {
                val updatedStations = cat.stations + station
                playlist[targetIdx] = cat.copy(stations = updatedStations)
            }
        } else {
            val newCat = Category(
                id = "cat_${cleanName.lowercase().replace(" ", "_")}",
                name = cleanName,
                stations = listOf(station)
            )
            playlist.add(newCat)
        }
        savePlaylistToDisk()
    }

    suspend fun nextCategory() = mutex.withLock {
        if (playlist.isEmpty()) return@withLock
        val current = _state.value
        val currentCatIdx = if (current.categoryIndex in playlist.indices) current.categoryIndex else 0
        var nextCatIdx = currentCatIdx
        val skipped = current.skippedCategoryIds

        for (i in 1..playlist.size) {
            val candidate = (currentCatIdx + i) % playlist.size
            if (!skipped.contains(playlist[candidate].id) || i == playlist.size) {
                nextCatIdx = candidate
                break
            }
        }
        commitState(nextCatIdx, null)
    }

    suspend fun previousCategory() = mutex.withLock {
        if (playlist.isEmpty()) return@withLock
        val current = _state.value
        val currentCatIdx = if (current.categoryIndex in playlist.indices) current.categoryIndex else 0
        var prevCatIdx = currentCatIdx
        val skipped = current.skippedCategoryIds

        for (i in 1..playlist.size) {
            val candidate = if (currentCatIdx - i < 0) (playlist.size + ((currentCatIdx - i) % playlist.size)) % playlist.size else currentCatIdx - i
            if (!skipped.contains(playlist[candidate].id) || i == playlist.size) {
                prevCatIdx = candidate
                break
            }
        }
        commitState(prevCatIdx, null)
    }

    suspend fun selectCategory(categoryIndex: Int) = mutex.withLock {
        commitState(categoryIndex) // Recalls last position or defaults to 0 on first visit
    }

    suspend fun selectStation(categoryIndex: Int, stationIndex: Int) = mutex.withLock {
        commitState(categoryIndex, stationIndex)
    }

    suspend fun moveStationUp(categoryIndex: Int, stationIndex: Int) = mutex.withLock {
        val category = playlist.getOrNull(categoryIndex) ?: return@withLock
        if (stationIndex <= 0 || stationIndex >= category.stations.size) return@withLock

        val mutableStations = category.stations.toMutableList()
        val temp = mutableStations[stationIndex]
        mutableStations[stationIndex] = mutableStations[stationIndex - 1]
        mutableStations[stationIndex - 1] = temp

        playlist[categoryIndex] = category.copy(stations = mutableStations)
        savePlaylistToDisk()

        val currentCatIdx = _state.value.categoryIndex
        val currentStatIdx = _state.value.stationIndex
        if (currentCatIdx == categoryIndex) {
            val newStatIdx = when (currentStatIdx) {
                stationIndex -> stationIndex - 1
                stationIndex - 1 -> stationIndex
                else -> currentStatIdx
            }
            commitState(categoryIndex, newStatIdx)
        } else {
            _state.update { it.copy(currentCategory = playlist[categoryIndex]) }
        }
    }

    suspend fun moveStationDown(categoryIndex: Int, stationIndex: Int) = mutex.withLock {
        val category = playlist.getOrNull(categoryIndex) ?: return@withLock
        if (stationIndex < 0 || stationIndex >= category.stations.size - 1) return@withLock

        val mutableStations = category.stations.toMutableList()
        val temp = mutableStations[stationIndex]
        mutableStations[stationIndex] = mutableStations[stationIndex + 1]
        mutableStations[stationIndex + 1] = temp

        playlist[categoryIndex] = category.copy(stations = mutableStations)
        savePlaylistToDisk()

        val currentCatIdx = _state.value.categoryIndex
        val currentStatIdx = _state.value.stationIndex
        if (currentCatIdx == categoryIndex) {
            val newStatIdx = when (currentStatIdx) {
                stationIndex -> stationIndex + 1
                stationIndex + 1 -> stationIndex
                else -> currentStatIdx
            }
            commitState(categoryIndex, newStatIdx)
        } else {
            _state.update { it.copy(currentCategory = playlist[categoryIndex]) }
        }
    }


    suspend fun searchAndSelectStation(stationId: String) = mutex.withLock {
        for (catIdx in playlist.indices) {
            val cat = playlist[catIdx]
            val statIdx = cat.stations.indexOfFirst { it.id == stationId }
            if (statIdx != -1) {
                commitState(catIdx, statIdx)
                break
            }
        }
    }

    fun getAllStationsWithCategory(): List<Pair<Category, Station>> {
        val list = mutableListOf<Pair<Category, Station>>()
        for (cat in playlist) {
            for (station in cat.stations) {
                list.add(cat to station)
            }
        }
        return list
    }

    suspend fun fetchTrackDetailsOnline(artist: String?, title: String?, stationName: String?): TrackInfoResult {
        val query = when {
            !artist.isNullOrBlank() && !title.isNullOrBlank() && artist != "Live Radio Stream" -> "$artist $title"
            !title.isNullOrBlank() -> title
            !artist.isNullOrBlank() -> artist
            else -> stationName ?: "Music Radio"
        }

        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val searchUrl = URL("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json")
                val conn = searchUrl.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Music2RadioApp/1.0")
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()

                    val json = JSONObject(text)
                    val searchArr = json.optJSONObject("query")?.optJSONArray("search")
                    if (searchArr != null && searchArr.length() > 0) {
                        val firstItem = searchArr.getJSONObject(0)
                        val pageTitle = firstItem.optString("title")
                        val snippet = firstItem.optString("snippet")
                            .replace(Regex("<[^>]*>"), "")
                            .replace("&quot;", "\"")
                            .replace("&#039;", "'")
                            .replace("&amp;", "&")

                        val encodedPageTitle = URLEncoder.encode(pageTitle, "UTF-8")
                        val summaryUrl = URL("https://en.wikipedia.org/api/rest_v1/page/summary/$encodedPageTitle")
                        val sumConn = summaryUrl.openConnection() as HttpURLConnection
                        sumConn.setRequestProperty("User-Agent", "Music2RadioApp/1.0")
                        sumConn.connectTimeout = 4000
                        sumConn.readTimeout = 4000

                        if (sumConn.responseCode == 200) {
                            val sumText = sumConn.inputStream.bufferedReader().readText()
                            sumConn.disconnect()
                            val sumJson = JSONObject(sumText)
                            val extract = sumJson.optString("extract").ifBlank { snippet }
                            val pageUrl = sumJson.optJSONObject("content_urls")
                                ?.optJSONObject("desktop")?.optString("page", "") ?: "https://en.wikipedia.org/wiki/$encodedPageTitle"

                            return@withContext TrackInfoResult(
                                title = pageTitle,
                                description = extract,
                                wikiUrl = pageUrl,
                                query = query
                            )
                        }

                        return@withContext TrackInfoResult(
                            title = pageTitle,
                            description = snippet,
                            wikiUrl = "https://en.wikipedia.org/wiki/$encodedPageTitle",
                            query = query
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return@withContext TrackInfoResult(
                title = query,
                description = "No detailed Wikipedia article found automatically for '$query'. Tap below to search directly on Google.",
                wikiUrl = null,
                query = query
            )
        }
    }

    suspend fun searchOnlineStations(query: String): List<Station> {
        val results = mutableListOf<Station>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://de1.api.radio-browser.info/json/stations/byname/$encodedQuery?limit=50&hidebroken=true")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MusicStreamApp/1.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.optString("name", "Unknown Station").trim()
                    val streamUrl = obj.optString("url_resolved", "").trim()
                    val id = obj.optString("stationuuid", i.toString())
                    if (streamUrl.isNotEmpty()) {
                        results.add(Station(id = id, name = name, streamUrl = streamUrl))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }
}