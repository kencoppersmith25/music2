package com.coppersmith.music2.ads

import com.coppersmith.music2.BuildConfig

object AdConfig {
    val IS_DEBUG: Boolean = BuildConfig.DEBUG

    // Official Google AdMob Test IDs (100% safe for development & testing):
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

    // Real Publisher IDs (for Release builds):
    const val REAL_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val REAL_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val REAL_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

    val BANNER_AD_UNIT_ID: String
        get() = if (IS_DEBUG) TEST_BANNER_ID else REAL_BANNER_ID

    val INTERSTITIAL_AD_UNIT_ID: String
        get() = if (IS_DEBUG) TEST_INTERSTITIAL_ID else REAL_INTERSTITIAL_ID

    const val SHOW_BANNERS = true
    const val SHOW_INTERSTITIALS = true
}