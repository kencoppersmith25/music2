package com.coppersmith.music1chat.coordinator

import com.coppersmith.music1chat.models.Category
import com.coppersmith.music1chat.persistence.SavedSearchCategory
import com.coppersmith.music1chat.session.PlaybackSessionState

sealed interface CategoryNavigationRequest {
    data object Empty : CategoryNavigationRequest

    data class Queued(
        val direction: Int,
        val pendingKey: String
    ) : CategoryNavigationRequest

    data class Activate(
        val currentKey: String,
        val selectedKey: String,
        val searchQuery: String?,
        val categoryId: Long?,
        val searchCached: Boolean
    ) : CategoryNavigationRequest
}

data class CategoryNavigationCompletion(
    val succeeded: Boolean,
    val activatedKey: String,
    val queuedDirection: Int?
)

sealed interface NavigationItem {
    val key: String
    val displayName: String
}

data class CategoryNavigationItem(
    val category: Category,
    val hasStations: Boolean
) : NavigationItem {
    override val key = "category:${category.id}"
    override val displayName = category.name
}

data class SearchNavigationItem(
    val query: String,
    val isCached: Boolean
) : NavigationItem {
    override val key = "search:${query.lowercase().trim()}"
    override val displayName = "Search: $query"
}

class MainScreenNavigationCoordinator {
    private var pendingNavigationKey: String? = null
    private var queuedDirection: Int? = null

    companion object {
        const val CATEGORY_PREFIX = "category:"
        const val SEARCH_PREFIX = "search:"
    }

    fun requestNavigation(
        direction: Int,
        currentState: PlaybackSessionState,
        permanentCategories: List<Category>,
        savedSearches: List<SavedSearchCategory>,
        categoryHasStations: (Long) -> Boolean,
        searchIsCached: (String) -> Boolean
    ): CategoryNavigationRequest {
        pendingNavigationKey?.let { pendingKey ->
            queuedDirection = direction
            return CategoryNavigationRequest.Queued(
                direction = direction,
                pendingKey = pendingKey
            )
        }

        val items = buildList {
            permanentCategories.forEach { category ->
                // Respect the navigation arrow toggle for static categories
                if (category.includedInNavigation && categoryHasStations(category.id)) {
                    add(CategoryNavigationItem(category, hasStations = true))
                }
            }
            savedSearches.filter { it.navigationEnabled }.forEach { search ->
                val query = search.query.trim()
                if (query.isNotBlank()) {
                    add(SearchNavigationItem(query, isCached = searchIsCached(query)))
                }
            }
        }

        if (items.isEmpty()) {
            return CategoryNavigationRequest.Empty
        }

        val currentKey = keyFor(currentState)
        val currentIndex = items.indexOfFirst { it.key.equals(currentKey, ignoreCase = true) }
            .coerceAtLeast(0)

        val size = items.size
        val nextIndex = (currentIndex + direction).mod(size)
        val targetItem = items[nextIndex]

        pendingNavigationKey = targetItem.key

        val searchQuery = (targetItem as? SearchNavigationItem)?.query

        return CategoryNavigationRequest.Activate(
            currentKey = currentKey,
            selectedKey = targetItem.key,
            searchQuery = searchQuery,
            categoryId = (targetItem as? CategoryNavigationItem)?.category?.id,
            searchCached = searchQuery?.let(searchIsCached) ?: false
        )
    }

    fun finishNavigation(
        requestedKey: String,
        activatedState: PlaybackSessionState
    ): CategoryNavigationCompletion {
        val matches = pendingNavigationKey == null || pendingNavigationKey.equals(requestedKey, ignoreCase = true)
        val queued = queuedDirection

        pendingNavigationKey = null
        queuedDirection = null

        return CategoryNavigationCompletion(
            succeeded = matches,
            activatedKey = requestedKey,
            queuedDirection = queued
        )
    }

    fun navigationKeys(
        permanentCategories: List<Category>,
        savedSearches: List<SavedSearchCategory>,
        categoryHasStations: (Long) -> Boolean
    ): List<String> {
        return buildList {
            permanentCategories.forEach { category ->
                if (category.includedInNavigation && categoryHasStations(category.id)) {
                    add("$CATEGORY_PREFIX${category.id}")
                }
            }
            savedSearches.filter { it.navigationEnabled }.forEach { search ->
                val query = search.query.trim()
                val searchKey = "$SEARCH_PREFIX${normalizeSearchName(query)}"
                if (query.isNotBlank() && !contains(searchKey)) {
                    add(searchKey)
                }
            }
        }
    }

    private fun keyFor(state: PlaybackSessionState): String {
        return if (state.isSearch && state.categoryName.isNotBlank()) {
            "$SEARCH_PREFIX${normalizeSearchName(state.categoryName).lowercase()}"
        } else {
            state.categoryId?.let { "$CATEGORY_PREFIX$it" }.orEmpty()
        }
    }

    private fun normalizeKey(key: String): String {
        return key
            .removePrefix(SEARCH_PREFIX)
            .removePrefix(CATEGORY_PREFIX)
            .removePrefix("Search:")
            .removePrefix("search:")
            .trim()
    }

    private fun normalizeSearchName(name: String?): String {
        if (name == null) return ""
        return name
            .removePrefix("Search:")
            .removePrefix("search:")
            .trim()
    }
}