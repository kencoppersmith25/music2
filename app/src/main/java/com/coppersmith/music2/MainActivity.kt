package com.coppersmith.music2

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
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

        // Start the playback service so it wakes up and begins listening for state changes
        val serviceIntent = Intent(this, MusicPlaybackService::class.java)
        startService(serviceIntent)

        setContent {
            MaterialTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}