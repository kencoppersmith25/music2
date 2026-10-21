package com.coppersmith.music2.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.coppersmith.music2.diagnostics.RideLogger
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

enum class AdReason {
    FIRST_MANUAL_PLAY,
    OPEN_SETTINGS,
    STATION_CHANGE,
    RETURN_TO_APP
}

object AdManager {

    private val initialized = AtomicBoolean(false)
    private val interstitialShowing = AtomicBoolean(false)
    private var mInterstitialAd: InterstitialAd? = null

    private var applicationStartedAtMs = 0L
    private var lastInterstitialShownAtMs = 0L

    // Minimum interval between interstitial ads (2 minutes)
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 120_000L

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) {
            return
        }

        applicationStartedAtMs = SystemClock.elapsedRealtime()

        try {
            MobileAds.initialize(context.applicationContext) { status ->
                RideLogger.log("ADMOB_INITIALIZED debug=${AdConfig.IS_DEBUG} status=${status.adapterStatusMap}")
                loadInterstitial(context.applicationContext)
            }
        } catch (e: Exception) {
            RideLogger.log("ADMOB_INIT_ERROR error='${e.message}'")
        }
    }

    fun loadInterstitial(context: Context) {
        if (!AdConfig.SHOW_INTERSTITIALS) return

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AdConfig.INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    RideLogger.log("ADMOB_INTERSTITIAL_LOADED unitId=${AdConfig.INTERSTITIAL_AD_UNIT_ID}")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    mInterstitialAd = null
                    RideLogger.log("ADMOB_INTERSTITIAL_LOAD_FAILED code=${loadAdError.code} msg=${loadAdError.message}")
                }
            }
        )
    }

    fun mayShowBanner(): Boolean {
        return initialized.get() && AdConfig.SHOW_BANNERS
    }

    fun maybeShowInterstitial(
        activity: Activity?,
        reason: AdReason,
        onDismissed: () -> Unit = {}
    ) {
        if (activity == null || !AdConfig.SHOW_INTERSTITIALS || !initialized.get()) {
            onDismissed()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val minInterval = if (AdConfig.IS_DEBUG) 5_000L else 120_000L

        if (now - lastInterstitialShownAtMs < minInterval && lastInterstitialShownAtMs != 0L) {
            onDismissed()
            return
        }

        val ad = mInterstitialAd
        if (ad == null) {
            loadInterstitial(activity.applicationContext)
            onDismissed()
            return
        }

        if (!interstitialShowing.compareAndSet(false, true)) {
            onDismissed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialShowing.set(false)
                mInterstitialAd = null
                lastInterstitialShownAtMs = SystemClock.elapsedRealtime()
                RideLogger.log("ADMOB_INTERSTITIAL_DISMISSED reason=$reason")
                loadInterstitial(activity.applicationContext)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                interstitialShowing.set(false)
                mInterstitialAd = null
                RideLogger.log("ADMOB_INTERSTITIAL_SHOW_FAILED error='${error.message}'")
                onDismissed()
            }
        }

        RideLogger.log("ADMOB_INTERSTITIAL_SHOWING reason=$reason")
        ad.show(activity)
    }
}