package com.coppersmith.music2.util

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SoundFeedback {

    fun playStationChangeTone() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
            CoroutineScope(Dispatchers.IO).launch {
                delay(500)
                toneGenerator.release()
            }
        } catch (_: Exception) {}
    }

    fun playCategoryChangeTone() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 100)
            CoroutineScope(Dispatchers.IO).launch {
                delay(120)
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_LOW_L, 120)
                delay(500)
                toneGenerator.release()
            }
        } catch (_: Exception) {}
    }
}