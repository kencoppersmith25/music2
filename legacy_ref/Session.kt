package com.coppersmith.music1chat.coordinator

import com.coppersmith.music1chat.models.Category
import com.coppersmith.music1chat.models.Station
import com.coppersmith.music1chat.persistence.SavedSearchCategory
import com.coppersmith.music1chat.repository.MusicRepository
import com.coppersmith.music1chat.session.PlaybackSessionController
import com.coppersmith.music1chat.session.PlaybackSessionMode
import com.coppersmith.music1chat.session.PlaybackSessionState

data class SessionSaveResult(
    val savedSearches: List<SavedSearchCategory>,
    val activeSearchQuery: String?
)

class PlaybackSessionManager(
    private val musicRepository: MusicRepository? = null
) {
    val navigationCoordinator = MainScreenNavigationCoordinator()

    fun save(
        state: PlaybackSessionState,
        wasPlaying: Boolean,
        savedSearches: List<SavedSearchCategory>,
        activeSearchQuery: String?,
        searchAnchorCategoryId: Long?,
        promoteToFront: Boolean
    ): SessionSaveResult {
        return SessionSaveResult(
            savedSearches = savedSearches,
            activeSearchQuery = activeSearchQuery
        )
    }

    fun restoreCachedSearch(
        query: String,
        cachedSessions: Map<String, PlaybackSessionState>,
        startPlayback: Boolean
    ): PlaybackSessionState? {
        val normalized = query.trim().lowercase()
        return cachedSessions.entries.firstOrNull {
            it.key.lowercase() == normalized || it.value.categoryName.equals(query, ignoreCase = true)
        }?.value
    }

    fun selectCategory(
        category: Category,
        stations: List<Station>,
        preferredStationId: Long? = null,
        startPlayback: Boolean = true
    ): PlaybackSessionState {
        val index = stations.indexOfFirst { it.id == preferredStationId }.takeIf { it >= 0 } ?: 0

        return PlaybackSessionState(
            mode = PlaybackSessionMode.CATEGORY,
            categoryId = category.id,
            categoryName = category.name,
            stations = stations,
            currentIndex = index,
            playbackRequested = startPlayback
        )
    }

    fun categoryById(categoryId: Long?): Category? {
        if (categoryId == null) return null
        return musicRepository?.categories?.getById(categoryId)
    }

    fun requestCategoryNavigation(
        direction: Int,
        currentState: PlaybackSessionState,
        permanentCategories: List<Category>,
        savedSearches: List<SavedSearchCategory>,
        cachedSessions: Map<String, PlaybackSessionState>,
        categoryHasStations: (Long) -> Boolean
    ): CategoryNavigationRequest {
        return navigationCoordinator.requestNavigation(
            direction = direction,
            currentState = currentState,
            permanentCategories = permanentCategories,
            savedSearches = savedSearches,
            categoryHasStations = categoryHasStations,
            searchIsCached = { query -> cachedSessions.containsKey(query) }
        )
    }

    fun finishCategoryNavigation(
        requestedKey: String,
        activatedState: PlaybackSessionState
    ): CategoryNavigationCompletion {
        return navigationCoordinator.finishNavigation(requestedKey, activatedState)
    }

    fun getNavigationKeys(
        permanentCategories: List<Category>,
        savedSearches: List<SavedSearchCategory>,
        categoryHasStations: (Long) -> Boolean
    ): List<String> {
        return navigationCoordinator.navigationKeys(
            permanentCategories = permanentCategories,
            savedSearches = savedSearches,
            categoryHasStations = categoryHasStations
        )
    }
}