package com.coppersmith.music2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import com.coppersmith.music2.service.MusicPlaybackService
import com.coppersmith.music2.ui.MusicViewModel
import com.coppersmith.music2.ui.screen.MainScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

class MainActivity : FragmentActivity() {

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
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    AppSplashScreen(onFinish = { showSplash = false })
                } else {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppSplashScreen(onFinish: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000L)
        onFinish()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "No Hands Radio",
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .aspectRatio(1f),
            contentScale = ContentScale.Fit
        )
    }
}