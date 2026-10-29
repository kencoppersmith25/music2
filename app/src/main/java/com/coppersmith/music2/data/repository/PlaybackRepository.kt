@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.coppersmith.music2.data.repository

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.coppersmith.music2.data.HardcodedDataSource
import com.coppersmith.music2.data.model.Category
import com.coppersmith.music2.data.model.PlayerState
import com.coppersmith.music2.data.model.Station
import com.coppersmith.music2.diagnostics.RideLogger
import com.coppersmith.music2.util.SoundFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        // MainActivity calls initRepository(context) to restore saved user categories and playback selection
    }

    fun initRepository(context: Context) {
        appContext = context.applicationContext

        scope.launch(Dispatchers.Main) {
            try {
                if (cachedCastContext == null) {
                    cachedCastContext = com.google.android.gms.cast.framework.CastContext.getSharedInstance(context.applicationContext)
                }
            } catch (_: Exception) {}
        }

        if (_state.value.currentCategory != null) return

        scope.launch(Dispatchers.IO) {
            val (savedCatIdx, savedStatIdx) = loadPlaybackSelectionFromDisk(context.applicationContext)
            val prefs = context.getSharedPreferences("music2_user_data", Context.MODE_PRIVATE)
            val hasSavedPlaylist = prefs.contains("saved_playlist_json")
            val loaded = loadPlaylistFromDisk(context.applicationContext)

            mutex.withLock {
                if (hasSavedPlaylist) {
                    playlist.clear()
                    playlist.addAll(loaded)
                } else {
                    // First run ever: populate default playlist with Mixed category
                    if (playlist.isEmpty()) {
                        playlist.add(Category(id = "cat_mixed", name = "Mixed", stations = HardcodedDataSource.initialMixedStations))
                        savePlaylistToDisk(context.applicationContext)
                    }
                }

                // Restore exact saved category and station selection without blaring sound on cold launch
                if (_state.value.currentCategory == null && playlist.isNotEmpty()) {
                    val safeCatIdx = savedCatIdx.coerceIn(0, playlist.size - 1)
                    val cat = playlist[safeCatIdx]
                    val safeStatIdx = if (cat.stations.isNotEmpty()) savedStatIdx.coerceIn(0, cat.stations.size - 1) else 0
                    commitState(safeCatIdx, safeStatIdx, autoPlay = false)
                } else if (playlist.isEmpty()) {
                    _state.update { PlayerState() }
                }
            }
        }
    }

    private fun savePlaybackSelectionToDisk(context: Context? = null, catIdx: Int, statIdx: Int) {
        val ctx = context ?: appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("music2_user_data", Context.MODE_PRIVATE)
            val skippedCatsJson = JSONArray(_state.value.skippedCategoryIds).toString()
            val skippedStatsJson = JSONArray(_state.value.skippedStationIds).toString()
            prefs.edit()
                .putInt("saved_category_index", catIdx)
                .putInt("saved_station_index", statIdx)
                .putString("saved_skipped_cats", skippedCatsJson)
                .putString("saved_skipped_stats", skippedStatsJson)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPlaybackSelectionFromDisk(context: Context): Pair<Int, Int> {
        try {
            val prefs = context.getSharedPreferences("music2_user_data", Context.MODE_PRIVATE)
            val catIdx = prefs.getInt("saved_category_index", 0)
            val statIdx = prefs.getInt("saved_station_index", 0)
            val themeMode = prefs.getString("saved_theme_mode", "AUTO") ?: "AUTO"

            val skippedCatsSet = mutableSetOf<String>()
            val skippedCatsJson = prefs.getString("saved_skipped_cats", null)
            if (!skippedCatsJson.isNullOrBlank()) {
                val arr = JSONArray(skippedCatsJson)
                for (i in 0 until arr.length()) skippedCatsSet.add(arr.getString(i))
            }

            val skippedStatsSet = mutableSetOf<String>()
            val skippedStatsJson = prefs.getString("saved_skipped_stats", null)
            if (!skippedStatsJson.isNullOrBlank()) {
                val arr = JSONArray(skippedStatsJson)
                for (i in 0 until arr.length()) skippedStatsSet.add(arr.getString(i))
            }

            _state.update {
                it.copy(
                    themeMode = themeMode,
                    isLightMode = (themeMode == "LIGHT"),
                    skippedCategoryIds = skippedCatsSet,
                    skippedStationIds = skippedStatsSet
                )
            }
            return catIdx to statIdx
        } catch (e: Exception) {
            return 0 to 0
        }
    }

    fun setThemeMode(mode: String) {
        _state.update { it.copy(themeMode = mode, isLightMode = (mode == "LIGHT")) }
        appContext?.let { ctx ->
            ctx.getSharedPreferences("music2_user_data", Context.MODE_PRIVATE)
                .edit().putString("saved_theme_mode", mode).apply()
        }
    }

    fun toggleLightMode() {
        val newMode = if (_state.value.themeMode == "LIGHT") "DARK" else "LIGHT"
        setThemeMode(newMode)
    }

    private fun savePlaylistToDisk(context: Context? = null) {
        val ctx = context ?: appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("music2_user_data", Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            val skippedCats = _state.value.skippedCategoryIds
            val skippedStats = _state.value.skippedStationIds

            for (cat in playlist) {
                val catObj = JSONObject()
                catObj.put("id", cat.id)
                catObj.put("name", cat.name)
                catObj.put("isSkipped", skippedCats.contains(cat.id))

                val stationsArr = JSONArray()
                for (stat in cat.stations) {
                    val statObj = JSONObject()
                    statObj.put("id", stat.id)
                    statObj.put("name", stat.name)
                    statObj.put("streamUrl", stat.streamUrl)
                    statObj.put("isSkipped", skippedStats.contains(stat.id))
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
                val restoredSkippedCats = _state.value.skippedCategoryIds.toMutableSet()
                val restoredSkippedStats = _state.value.skippedStationIds.toMutableSet()

                for (i in 0 until jsonArray.length()) {
                    val catObj = jsonArray.getJSONObject(i)
                    val catId = catObj.optString("id", "cat_$i")
                    val catName = catObj.optString("name", "Category $i")
                    if (catObj.optBoolean("isSkipped", false)) {
                        restoredSkippedCats.add(catId)
                    }

                    val stationsArr = catObj.optJSONArray("stations") ?: JSONArray()
                    val stationsList = mutableListOf<Station>()
                    for (j in 0 until stationsArr.length()) {
                        val statObj = stationsArr.getJSONObject(j)
                        val statId = statObj.optString("id", "stat_${i}_$j")
                        val statName = statObj.optString("name", "Station $j")
                        val streamUrl = statObj.optString("streamUrl", "")
                        if (statObj.optBoolean("isSkipped", false)) {
                            restoredSkippedStats.add(statId)
                        }
                        if (streamUrl.isNotBlank()) {
                            stationsList.add(Station(id = statId, name = statName, streamUrl = streamUrl))
                        }
                    }
                    if (stationsList.isNotEmpty()) {
                        result.add(Category(id = catId, name = catName, stations = stationsList))
                    }
                }

                _state.update { current ->
                    current.copy(
                        skippedCategoryIds = restoredSkippedCats,
                        skippedStationIds = restoredSkippedStats
                    )
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

    private var startupWatchdogJob: Job? = null

    fun setStationTimeoutSeconds(seconds: Int) {
        val safeSeconds = seconds.coerceIn(2, 300)
        _state.update { it.copy(stationTimeoutSeconds = safeSeconds) }
    }

    fun setWhilePlayingTimeoutSeconds(seconds: Int) {
        val safeSeconds = seconds.coerceIn(2, 300)
        _state.update { it.copy(whilePlayingTimeoutSeconds = safeSeconds) }
    }

    fun setLoading(loading: Boolean) {
        _state.update { it.copy(isLoading = loading) }
    }

    fun setPlaying(playing: Boolean) {
        if (playing) {
            cancelStartupWatchdog()
        }
        _state.update { it.copy(isPlaying = playing) }
    }

    private fun cancelStartupWatchdog() {
        startupWatchdogJob?.cancel()
        startupWatchdogJob = null
    }

    private fun startStartupWatchdog(station: Station) {
        cancelStartupWatchdog()
        val timeoutMs = 3500L
        startupWatchdogJob = scope.launch(Dispatchers.IO) {
            delay(timeoutMs)
            if (!_state.value.isPlaying) {
                RideLogger.log("STATION_STARTUP_TIMEOUT station='${station.name}' url='${station.streamUrl}' timeoutMs=$timeoutMs -> Auto-advancing")
                withContext(Dispatchers.Main) {
                    nextStation()
                }
            }
        }
    }

    fun updateMetadata(title: String?, artist: String?) {
        val cleanTitle = title?.trim()?.ifBlank { null }
        val cleanArtist = artist?.trim()?.ifBlank { null }
        _state.update {
            it.copy(
                trackTitle = cleanTitle,
                artistName = cleanArtist
            )
        }
        val currentCat = _state.value.currentCategory?.name ?: "Radio"
        val currentStat = _state.value.currentStation?.name ?: "Live Stream"
        scope.launch(Dispatchers.Main) {
            exoPlayer?.let { player ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(cleanTitle ?: currentStat)
                    .setArtist(cleanArtist ?: "Live Radio Stream")
                    .setAlbumTitle(currentCat)
                    .setDisplayTitle(currentStat)
                    .setSubtitle(cleanArtist ?: currentStat)
                    .build()
                player.playlistMetadata = metadata
            }
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
        val currentSet = _state.value.skippedCategoryIds.toMutableSet()
        if (currentSet.contains(category.id)) {
            currentSet.remove(category.id)
        } else {
            currentSet.add(category.id)
        }
        _state.update { it.copy(skippedCategoryIds = currentSet) }
        savePlaybackSelectionToDisk(appContext, _state.value.categoryIndex, _state.value.stationIndex)
        savePlaylistToDisk(appContext)
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

        savePlaybackSelectionToDisk(appContext, safeCatIdx, safeStatIdx)
        RideLogger.log("COMMIT_STATE catIdx=$safeCatIdx statIdx=$safeStatIdx category='${category.name}' station='${station.name}'")

        scope.launch(Dispatchers.Main) {
            exoPlayer?.let { player ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(_state.value.artistName ?: "Live Radio Stream")
                    .setAlbumTitle(category.name)
                    .setDisplayTitle(station.name)
                    .setSubtitle(_state.value.artistName ?: "Live Radio Stream")
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(station.streamUrl)
                    .setMediaMetadata(metadata)
                    .build()

                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            }
        }
        startStartupWatchdog(station)
    }

    suspend fun togglePlayPause() = mutex.withLock {
        val currentState = _state.value
        if (currentState.currentStation == null && playlist.isNotEmpty()) {
            commitState(0, 0)
        } else {
            val willPlay = !currentState.isPlaying
            val session = activeCastSession
            if (session != null && session.isConnected) {
                val remoteMediaClient = session.remoteMediaClient
                if (remoteMediaClient != null) {
                    if (willPlay) {
                        if (remoteMediaClient.isPaused) {
                            remoteMediaClient.play()
                        } else {
                            currentState.currentStation?.let { playOnCastSession(session, it) }
                        }
                    } else {
                        remoteMediaClient.pause()
                    }
                }
            } else {
                if (willPlay) {
                    currentState.currentStation?.let { startStartupWatchdog(it) }
                } else {
                    exoPlayer?.pause()
                }
            }
            _state.update { it.copy(isPlaying = willPlay) }
        }
    }

    private fun fetchStationsForTerm(baseUrl: String, field: String, term: String, limit: Int): List<Station> {
        val results = mutableListOf<Station>()
        try {
            val encodedTag = URLEncoder.encode(term.trim(), "UTF-8")
            val urlString = if (field == "byname") {
                "$baseUrl/json/stations/byname/$encodedTag?limit=$limit&hidebroken=true&lastcheckok=1"
            } else {
                "$baseUrl/json/stations/search?$field=$encodedTag&limit=$limit&order=votes&reverse=true&hidebroken=true&lastcheckok=1"
            }
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "NoHandsRadio/1.1 Android")
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.optString("name", "Unknown Station").trim()
                    val streamUrl = obj.optString("url_resolved", "").ifBlank { obj.optString("url", "") }.trim()
                    val id = obj.optString("stationuuid", i.toString())
                    val language = obj.optString("language", "").lowercase()
                    val countryCode = obj.optString("countrycode", "").uppercase()
                    val votes = obj.optInt("votes", 0)

                    val isEnglishOrUS = language.contains("english") || countryCode in listOf("US", "GB", "CA", "AU", "NZ", "IE")

                    if (streamUrl.isNotEmpty() && !streamUrl.contains("localhost")) {
                        results.add(Station(id = id, name = name, streamUrl = streamUrl, isEnglishOrUS = isEnglishOrUS, votes = votes))
                    }
                }
            }
            connection.disconnect()
        } catch (_: Exception) {}
        return results
    }

    suspend fun loadCategoryLive(categoryId: String, categoryName: String, tag: String) = mutex.withLock {
        val displayName = if (categoryName.startsWith("Search:", ignoreCase = true)) categoryName else "Search: ${categoryName.replaceFirstChar { it.uppercase() }}"
        val searchCatId = "cat_search_${tag.lowercase().replace(" ", "_")}"

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

                when {
                    queryLower.contains("africa") || queryLower.contains("african") -> {
                        searchTags.addAll(listOf("African", "Africa", "Afrobeats", "Afrobeat", "Nigeria", "Kenya", "Ghana", "South Africa", "Lagos", "Johannesburg", "Cairo", "Nairobi", "Casablanca", "Accra", "Cape Town", "Dakar", "Tunis", "Addis Ababa", "Uganda", "Tanzania", "Senegal", "Congo", "Mali", "Zimbabwe"))
                    }
                    queryLower.contains("hawai") || queryLower.contains("hawaiian") -> {
                        searchTags.addAll(listOf("Hawaiian", "Hawaii", "Honolulu", "Maui", "Kauai", "Kona", "Aloha", "Hilo", "Hawaii Music Live"))
                    }
                    queryLower.contains("classical") || queryLower.contains("symphony") || queryLower.contains("orchestra") || queryLower.contains("opera") -> {
                        searchTags.addAll(listOf("Classical", "Symphony", "Orchestra", "Opera", "Chamber", "Baroque", "Philharmonic", "Choir"))
                    }
                    queryLower.contains("jazz") || queryLower.contains("blues") -> {
                        searchTags.addAll(listOf("Jazz", "Blues", "Smooth Jazz", "New Orleans", "Chicago Jazz", "Bebop"))
                    }
                    queryLower.contains("electronic") || queryLower.contains("edm") || queryLower.contains("techno") || queryLower.contains("house") -> {
                        searchTags.addAll(listOf("Electronic", "Techno", "House", "Trance", "Ambient", "Ibiza", "Berlin Electronic"))
                    }
                    queryLower.contains("france") || queryLower.contains("french") -> {
                        searchTags.addAll(listOf("Paris", "Lyon", "Marseille", "Nice", "Bordeaux", "Toulouse", "France"))
                    }
                    queryLower.contains("germany") || queryLower.contains("german") -> {
                        searchTags.addAll(listOf("Berlin", "Munich", "Frankfurt", "Hamburg", "Cologne", "Germany"))
                    }
                    queryLower.contains("united kingdom") || queryLower.contains("uk") || queryLower.contains("british") || queryLower.contains("england") -> {
                        searchTags.addAll(listOf("London", "Manchester", "Birmingham", "Glasgow", "Edinburgh", "BBC", "United Kingdom"))
                    }
                    queryLower.contains("japan") || queryLower.contains("japanese") -> {
                        searchTags.addAll(listOf("Tokyo", "Osaka", "Kyoto", "Nagoya", "Fukuoka", "Japan"))
                    }
                    queryLower.contains("india") || queryLower.contains("indian") -> {
                        searchTags.addAll(listOf("Mumbai", "Delhi", "Bangalore", "Chennai", "Kolkata", "India"))
                    }
                    queryLower.contains("latin") || queryLower.contains("salsa") || queryLower.contains("samba") -> {
                        searchTags.addAll(listOf("Latin", "Salsa", "Bachata", "Reggaeton", "Cumbia", "Merengue"))
                    }
                    queryLower.contains("celtic") || queryLower.contains("irish") -> {
                        searchTags.addAll(listOf("Celtic", "Irish", "Dublin", "Galway", "Belfast", "Scotland"))
                    }
                    queryLower.contains("60s") || queryLower.contains("1960") -> {
                        searchTags.addAll(listOf("60s", "1960s", "sixties", "oldies", "60s gold", "60s pop", "60s rock"))
                    }
                    queryLower.contains("70s") || queryLower.contains("1970") -> {
                        searchTags.addAll(listOf("70s", "1970s", "seventies", "70s pop", "70s rock", "70s disco"))
                    }
                    queryLower.contains("80s") || queryLower.contains("1980") -> {
                        searchTags.addAll(listOf("80s", "1980s", "eighties", "80s pop", "80s rock", "synthpop"))
                    }
                    queryLower.contains("90s") || queryLower.contains("1990") -> {
                        searchTags.addAll(listOf("90s", "1990s", "nineties", "90s pop", "90s rock", "grunge"))
                    }
                    else -> {}
                }

                val rawStations = mutableListOf<Station>()
                val searchLimit = _state.value.searchResultLimit

                coroutineScope {
                    for (baseUrl in baseUrls) {
                        try {
                            val tagJobs = searchTags.map { t ->
                                async(Dispatchers.IO) { fetchStationsForTerm(baseUrl, "tag", t, searchLimit) }
                            }
                            val nameJobs = searchTags.map { t ->
                                async(Dispatchers.IO) { fetchStationsForTerm(baseUrl, "name", t, searchLimit) }
                            }
                            val byNameJob = async(Dispatchers.IO) { fetchStationsForTerm(baseUrl, "byname", tag, searchLimit) }

                            val tagResults = tagJobs.awaitAll().flatten()
                            val nameResults = nameJobs.awaitAll().flatten()
                            val byNameResults = byNameJob.await()

                            rawStations.addAll(tagResults)
                            rawStations.addAll(nameResults)
                            rawStations.addAll(byNameResults)

                            if (rawStations.size >= searchLimit) break
                        } catch (_: Exception) {}
                    }
                }

                // Prioritize English / US / UK / CA / AU stations (~80% primary, 20% international)
                val englishOrUs = rawStations.filter { it.isEnglishOrUS }.sortedByDescending { it.votes }
                val international = rawStations.filter { !it.isEnglishOrUS }.sortedByDescending { it.votes }

                val primaryQuota = (searchLimit * 0.8).toInt()
                val primarySelection = englishOrUs.take(primaryQuota)
                val secondarySelection = international.take(searchLimit - primarySelection.size)

                val combined = (primarySelection + secondarySelection + englishOrUs.drop(primaryQuota) + international.drop(searchLimit - primarySelection.size))
                    .distinctBy { it.streamUrl.lowercase().split('?')[0] }
                    .distinctBy { it.name.lowercase() }
                    .take(searchLimit)

                stations.addAll(combined)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            RideLogger.log("SEARCH_EXCEPTION query='$tag' error='${e.message}'")
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
        val currentSet = _state.value.skippedStationIds.toMutableSet()
        if (currentSet.contains(stationId)) {
            currentSet.remove(stationId)
        } else {
            currentSet.add(stationId)
        }
        _state.update { it.copy(skippedStationIds = currentSet) }
        savePlaybackSelectionToDisk(appContext, _state.value.categoryIndex, _state.value.stationIndex)
        savePlaylistToDisk(appContext)
    }

    fun nextStation() {
        SoundFeedback.playStationChangeTone()
        val currentCat = playlist.getOrNull(_state.value.categoryIndex) ?: return
        if (currentCat.stations.isEmpty()) return

        val currentStatIdx = _state.value.stationIndex
        val currentCatIdx = _state.value.categoryIndex
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

                if (nextStatIdx == -1) {
                    // Wrap around to the first valid station in the current category
                    nextStatIdx = currentCat.stations.indexOfFirst { !skippedStats.contains(it.id) }
                    if (nextStatIdx == -1) nextStatIdx = 0
                }

                commitState(currentCatIdx, nextStatIdx)
            }
        }
    }

    fun previousStation() {
        SoundFeedback.playStationChangeTone()
        val currentCat = playlist.getOrNull(_state.value.categoryIndex) ?: return
        if (currentCat.stations.isEmpty()) return

        val currentStatIdx = _state.value.stationIndex
        val currentCatIdx = _state.value.categoryIndex
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

                if (prevStatIdx == -1) {
                    // Wrap around to the last valid station in the current category (e.g. station 50)
                    prevStatIdx = currentCat.stations.indexOfLast { !skippedStats.contains(it.id) }
                    if (prevStatIdx == -1) prevStatIdx = (currentCat.stations.size - 1).coerceAtLeast(0)
                }

                commitState(currentCatIdx, prevStatIdx)
            }
        }
    }

    private val categoryLastStationMap = mutableMapOf<Int, Int>()

    private var activeCastSession: com.google.android.gms.cast.framework.CastSession? = null
    private var cachedCastContext: com.google.android.gms.cast.framework.CastContext? = null

    fun onCastSessionConnected(session: com.google.android.gms.cast.framework.CastSession) {
        activeCastSession = session
        _state.update { it.copy(isCasting = true) }
        RideLogger.log("CAST_CONNECTED session='${session.sessionId}'")
        val currentStat = _state.value.currentStation
        if (currentStat != null) {
            playOnCastSession(session, currentStat)
        }
    }

    fun onCastSessionDisconnected() {
        RideLogger.log("CAST_DISCONNECTED")
        activeCastSession = null
        _state.update { it.copy(isCasting = false) }
        if (_state.value.isPlaying) {
            exoPlayer?.playWhenReady = true
            exoPlayer?.prepare()
            exoPlayer?.play()
        }
    }

    private fun getConnectedCastSession(): com.google.android.gms.cast.framework.CastSession? {
        val session = activeCastSession
        if (session != null && session.isConnected) return session
        return try {
            cachedCastContext?.sessionManager?.currentCastSession?.takeIf { it.isConnected }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveStreamInfo(originalUrl: String): Pair<String, String> {
        return try {
            val url = URL(originalUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("User-Agent", "NoHandsRadio/1.1 Android")
            conn.connect()
            val finalUrl = conn.url.toString()
            val rawContentType = conn.contentType?.lowercase()?.split(";")?.get(0)?.trim()
            val mimeType = when {
                !rawContentType.isNullOrBlank() && rawContentType != "text/html" && rawContentType != "text/plain" && rawContentType != "application/octet-stream" -> rawContentType
                finalUrl.contains(".m3u8") || finalUrl.contains("m3u") -> "application/x-mpegURL"
                finalUrl.contains(".aac") || finalUrl.contains("aac") -> "audio/aac"
                finalUrl.contains(".ogg") -> "audio/ogg"
                else -> "audio/mpeg"
            }
            conn.disconnect()
            finalUrl to mimeType
        } catch (_: Exception) {
            val lower = originalUrl.lowercase()
            val fallbackMime = when {
                lower.contains(".m3u8") -> "application/x-mpegURL"
                lower.contains(".aac") -> "audio/aac"
                lower.contains(".ogg") -> "audio/ogg"
                else -> "audio/mpeg"
            }
            originalUrl to fallbackMime
        }
    }

    private fun inferMimeType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> "application/x-mpegURL"
            lower.contains(".mpd") -> "application/dash+xml"
            lower.contains(".aac") -> "audio/aac"
            lower.contains(".ogg") -> "audio/ogg"
            else -> "audio/mpeg"
        }
    }

    private fun playOnCastSession(session: com.google.android.gms.cast.framework.CastSession, station: Station) {
        scope.launch(Dispatchers.Main) {
            val remoteMediaClient = session.remoteMediaClient ?: return@launch
            try {
                exoPlayer?.pause()

                val (resolvedUrl, resolvedMime) = withContext(Dispatchers.IO) {
                    resolveStreamInfo(station.streamUrl)
                }

                val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
                castMeta.putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, station.name)
                castMeta.putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, _state.value.currentCategory?.name ?: "Live Radio Stream")

                val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(resolvedUrl)
                    .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType(resolvedMime)
                    .setMetadata(castMeta)
                    .build()

                val request = com.google.android.gms.cast.MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .setCurrentTime(0)
                    .build()

                try {
                    remoteMediaClient.stop()
                } catch (_: Exception) {}

                val pendingResult = remoteMediaClient.load(request)
                pendingResult.setResultCallback { result ->
                    if (result.status.isSuccess) {
                        RideLogger.log("CAST_LOAD_SUCCESS station='${station.name}' mime='$resolvedMime' url='$resolvedUrl'")
                    } else {
                        RideLogger.log("CAST_LOAD_FAILED code=${result.status.statusCode} msg='${result.status.statusMessage}'")
                    }
                }
                RideLogger.log("CAST_PLAYING_REMOTE station='${station.name}' mime='$resolvedMime' url='$resolvedUrl'")
            } catch (e: Exception) {
                RideLogger.log("CAST_PLAY_ERROR error='${e.message}'")
            }
        }
    }

    private fun commitState(categoryIndex: Int, stationIndex: Int? = null, autoPlay: Boolean = true) {
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

        savePlaybackSelectionToDisk(appContext, safeCatIdx, safeStatIdx)

        RideLogger.log("COMMIT_STATE catIdx=$safeCatIdx statIdx=$safeStatIdx category='${category.name}' station='${station.name}' autoPlay=$autoPlay")
        scope.launch(Dispatchers.Main) {
            val castSess = getConnectedCastSession()
            if (castSess != null && castSess.isConnected) {
                if (autoPlay) {
                    playOnCastSession(castSess, station)
                }
            } else if (autoPlay) {
                startStartupWatchdog(station)
            }
        }

        _state.update {
            it.copy(
                categoryIndex = safeCatIdx,
                stationIndex = safeStatIdx,
                currentCategory = category,
                currentStation = station,
                isPlaying = autoPlay,
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
            savePlaybackSelectionToDisk(appContext, -1, -1)
            exoPlayer?.stop()
            _state.update { PlayerState() }
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
            // Category has 0 stations left -> AUTOMATICALLY DELETE THE CATEGORY!
            playlist.removeAt(catIdx)
            categoryLastStationMap.remove(catIdx)
            savePlaylistToDisk()
            if (playlist.isNotEmpty()) {
                val nextCatIdx = catIdx.coerceIn(0, playlist.size - 1)
                commitState(nextCatIdx, 0)
            } else {
                savePlaybackSelectionToDisk(appContext, -1, -1)
                exoPlayer?.stop()
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
        SoundFeedback.playCategoryChangeTone()
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
        SoundFeedback.playCategoryChangeTone()
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
        SoundFeedback.playCategoryChangeTone()
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

    private fun queryWikipediaSummary(queryTerm: String): TrackInfoResult? {
        try {
            val encodedQuery = URLEncoder.encode(queryTerm, "UTF-8")
            val searchUrl = URL("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json")
            val conn = searchUrl.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Music2RadioApp/1.0")
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
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
                    sumConn.connectTimeout = 3000
                    sumConn.readTimeout = 3000

                    if (sumConn.responseCode == 200) {
                        val sumText = sumConn.inputStream.bufferedReader().use { it.readText() }
                        sumConn.disconnect()
                        val sumJson = JSONObject(sumText)
                        val extract = sumJson.optString("extract").ifBlank { snippet }
                        val pageUrl = sumJson.optJSONObject("content_urls")
                            ?.optJSONObject("desktop")?.optString("page", "") ?: "https://en.wikipedia.org/wiki/$encodedPageTitle"

                        if (extract.isNotBlank() && extract.length > 30) {
                            return TrackInfoResult(
                                title = pageTitle,
                                description = extract,
                                wikiUrl = pageUrl,
                                query = queryTerm
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun fetchTrackDetailsOnline(artist: String?, title: String?, stationName: String?): TrackInfoResult {
        return withContext(Dispatchers.IO) {
            val cleanArtist = artist?.trim()?.takeIf { it.isNotBlank() && it != "Live Radio Stream" }
            val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() }

            val queriesToTry = mutableListOf<String>()
            if (cleanArtist != null && cleanTitle != null) {
                queriesToTry.add("$cleanArtist $cleanTitle")
                queriesToTry.add(cleanTitle)
                queriesToTry.add(cleanArtist)
            } else if (cleanTitle != null) {
                queriesToTry.add(cleanTitle)
            } else if (cleanArtist != null) {
                queriesToTry.add(cleanArtist)
            }
            if (stationName != null) {
                queriesToTry.add(stationName)
            }

            for (q in queriesToTry) {
                val res = queryWikipediaSummary(q)
                if (res != null) {
                    return@withContext res.copy(
                        composerQuery = cleanArtist,
                        pieceQuery = cleanTitle,
                        stationQuery = stationName
                    )
                }
            }

            val fallbackQuery = cleanTitle ?: cleanArtist ?: stationName ?: "Music Radio"
            return@withContext TrackInfoResult(
                title = fallbackQuery,
                description = "No detailed Wikipedia summary found automatically for '$fallbackQuery'. Tap search chips below to search directly on Google for recordings, score history, composer details, and station info.",
                composerQuery = cleanArtist,
                pieceQuery = cleanTitle,
                stationQuery = stationName,
                wikiUrl = null,
                query = fallbackQuery
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