package com.coppersmith.music2.ads

import android.content.Context
import com.coppersmith.music2.diagnostics.RideLogger
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.atomic.AtomicBoolean

object AdManager {

    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) {
            return
        }

        try {
            MobileAds.initialize(context.applicationContext) { status ->
                RideLogger.log("ADMOB_INITIALIZED debug=${AdConfig.IS_DEBUG} status=${status.adapterStatusMap}")
            }
        } catch (e: Exception) {
            RideLogger.log("ADMOB_INIT_ERROR error='${e.message}'")
        }
    }

    fun mayShowBanner(): Boolean {
        return initialized.get() && AdConfig.SHOW_BANNERS
    }
}