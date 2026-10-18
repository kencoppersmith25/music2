//package com.coppersmith.music1chat.repository
//
//class MusicRepository {
//    val categories = CategoryRepository()
//    val stations = StationRepository()
//    val memberships = MembershipRepository(
//        stationRepository = stations
//    )
//
//    fun seedDefaults() {
//        categories.seedDefaults()
//        stations.seedDefaults()
//        memberships.seedDefaults()
//        stations.clearAllFailedFlags()
//    }
//}

package com.coppersmith.music1chat.repository

class MusicRepository {
    val categories = CategoryRepository()
    val stations = StationRepository()
    val memberships = MembershipRepository(
        stationRepository = stations
    )

    fun seedDefaults() {
        // Only seed the stations from StationRepository
        stations.seedDefaults()

        // If you want a single default category created automatically for these stations:
        if (categories.getAll().isEmpty()) {
            categories.add(
                com.coppersmith.music1chat.models.Category(
                    id = 1,
                    name = "Mixed",
                    type = com.coppersmith.music1chat.models.CategoryType.STANDARD,
                    sortOrder = 0
                )
            )
        }

        // Automatically map all seeded stations into this default category so they aren't orphaned
        stations.getAll().forEach { station ->
            if (!memberships.contains(1L, station.id)) {
                memberships.addStationToCategory(
                    categoryId = 1L,
                    stationId = station.id
                )
            }
        }

        stations.clearAllFailedFlags()
    }
}