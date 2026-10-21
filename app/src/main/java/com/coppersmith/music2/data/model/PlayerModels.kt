package com.coppersmith.music2.data.model

data class Station(
    val id: String,
    val name: String,
    val streamUrl: String,
    val isEnglishOrUS: Boolean = false,
    val votes: Int = 0
)

data class Category(
    val id: String,
    val name: String,
    val stations: List<Station>
)

data class PlayerState(
    val currentCategory: Category? = null,
    val currentStation: Station? = null,
    val categoryIndex: Int = -1,
    val stationIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val trackTitle: String? = null,
    val artistName: String? = null,
    val isContinuousNav: Boolean = true,
    val announceCategories: Boolean = true,
    val bluetoothBackAction: String = "NEXT_CATEGORY",
    val searchResultLimit: Int = 50,
    val stationTimeoutSeconds: Int = 5,
    val searchingCategoryName: String? = null,
    val searchError: String? = null,
    val isLightMode: Boolean = false,
    val themeMode: String = "AUTO", // Options: "AUTO", "LIGHT", "DARK"
    val skippedCategoryIds: Set<String> = emptySet(),
    val skippedStationIds: Set<String> = emptySet(),
    val recentlyPlayedTracks: List<PlayedTrack> = emptyList()
)

data class PlayedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val stationName: String,
    val timestampMs: Long
)

data class TrackInfoResult(
    val title: String,
    val description: String,
    val composerQuery: String? = null,
    val pieceQuery: String? = null,
    val stationQuery: String? = null,
    val wikiUrl: String? = null,
    val query: String
)