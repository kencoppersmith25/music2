package com.coppersmith.music2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import com.coppersmith.music2.service.MusicPlaybackService
import com.coppersmith.music2.ui.MusicViewModel
import com.coppersmith.music2.ui.screen.MainScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize RideLogger diagnostic logger
        com.coppersmith.music2.diagnostics.RideLogger.startAutomatically(this)

        // Initialize Repository with disk persistence
        com.coppersmith.music2.data.repository.PlaybackRepository.initRepository(this)

        // Initialize AdManager (Google Mobile Ads SDK)
        com.coppersmith.music2.ads.AdManager.initialize(this)

        // Start the playback service as a Foreground Service
        val serviceIntent = Intent(this, MusicPlaybackService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        setContent {
            MaterialTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}