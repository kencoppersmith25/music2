package com.coppersmith.music2.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coppersmith.music2.data.model.PlayerState
import com.coppersmith.music2.data.model.Category
import com.coppersmith.music2.data.model.Station
import com.coppersmith.music2.data.model.TrackInfoResult
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.coppersmith.music2.data.repository.PlaybackRepository

class MusicViewModel : ViewModel() {

    private val repository = PlaybackRepository
    val uiState: StateFlow<PlayerState> = repository.state

    fun onPlayPauseClicked() {
        viewModelScope.launch {
            repository.togglePlayPause()
        }
    }

    fun onNextClicked() {
        viewModelScope.launch {
            repository.nextStation()
        }
    }

    fun onPreviousClicked() {
        viewModelScope.launch {
            repository.previousStation()
        }
    }

    fun onStationSelected(categoryIndex: Int, stationIndex: Int) {
        viewModelScope.launch {
            repository.selectStation(categoryIndex, stationIndex)
        }
    }

    fun onMoveStationUp(categoryIndex: Int, stationIndex: Int) {
        viewModelScope.launch {
            repository.moveStationUp(categoryIndex, stationIndex)
        }
    }

    fun onMoveStationDown(categoryIndex: Int, stationIndex: Int) {
        viewModelScope.launch {
            repository.moveStationDown(categoryIndex, stationIndex)
        }
    }

    fun onNextCategoryClicked() {
        viewModelScope.launch {
            repository.nextCategory()
        }
    }

    fun onPreviousCategoryClicked() {
        viewModelScope.launch {
            repository.previousCategory()
        }
    }

    fun onDeleteCategoryClicked(categoryIndex: Int) {
        viewModelScope.launch {
            repository.deleteCategory(categoryIndex)
        }
    }

    fun onDeleteStationClicked() {
        viewModelScope.launch {
            repository.deleteCurrentStation()
        }
    }

    fun onSaveStationToCategory(station: Station, targetCategoryName: String) {
        viewModelScope.launch {
            repository.saveStationToCategory(station, targetCategoryName)
        }
    }

    fun onToggleContinuousNav() {
        repository.toggleContinuousNav()
    }

    fun onToggleAnnounceCategories() {
        repository.toggleAnnounceCategories()
    }

    fun onSetBluetoothBackAction(action: String) {
        repository.setBluetoothBackAction(action)
    }

    fun onSetSearchResultLimit(limit: Int) {
        repository.setSearchResultLimit(limit)
    }

    fun onClearSearchError() {
        repository.clearSearchError()
    }

    fun onToggleCategorySkipped(categoryIndex: Int) {
        repository.toggleCategorySkipped(categoryIndex)
    }

    fun onToggleStationSkipped(stationId: String) {
        repository.toggleStationSkipped(stationId)
    }

    fun onGetAllCategories(): List<Category> {
        return repository.playlist
    }

    fun onSearchStationSelected(stationId: String) {
        viewModelScope.launch {
            repository.searchAndSelectStation(stationId)
        }
    }

    fun onGetAllStations(): List<Pair<Category, Station>> {
        return repository.getAllStationsWithCategory()
    }

    suspend fun onSearchOnline(query: String): List<Station> {
        return repository.searchOnlineStations(query)
    }

    suspend fun fetchTrackDetailsOnline(artist: String?, title: String?, stationName: String?): TrackInfoResult {
        return repository.fetchTrackDetailsOnline(artist, title, stationName)
    }

    fun onCategorySelected(categoryIndex: Int) {
        viewModelScope.launch {
            repository.selectCategory(categoryIndex)
        }
    }

    fun onCategorySelected(categoryIndex: Int, category: Any?) {
        viewModelScope.launch {
            repository.selectCategory(categoryIndex)
        }
    }

    fun onCategorySelected(categoryId: String, categoryIndex: Int) {
        viewModelScope.launch {
            repository.selectCategory(categoryIndex)
        }
    }

    fun onCategorySelected(categoryId: String, categoryName: String, tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existingIndex = repository.playlist.indexOfFirst {
                it.id == categoryId || it.name.equals(categoryName, ignoreCase = true)
            }
            if (existingIndex != -1 && repository.playlist[existingIndex].stations.isNotEmpty()) {
                repository.selectCategory(existingIndex)
            } else {
                repository.loadCategoryLive(categoryId, categoryName, tag)
            }
        }
    }
}