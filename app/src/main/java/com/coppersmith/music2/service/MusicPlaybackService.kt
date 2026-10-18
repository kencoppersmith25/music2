package com.coppersmith.music2.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.coppersmith.music2.R
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.coppersmith.music2.data.repository.PlaybackRepository
import com.coppersmith.music2.diagnostics.RideLogger
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MusicPlaybackService : MediaSessionService(), TextToSpeech.OnInitListener {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val repository = PlaybackRepository

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var lastAnnouncedCategoryId: String? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                val currentState = repository.state.value
                val currentCategory = currentState.currentCategory
                if (currentCategory != null && currentState.isPlaying && currentState.announceCategories && lastAnnouncedCategoryId == null) {
                    lastAnnouncedCategoryId = currentCategory.id
                    Log.d("MusicPlaybackService", "Initial TTS announcement: ${currentCategory.name}")
                    textToSpeech?.speak(currentCategory.name, TextToSpeech.QUEUE_FLUSH, null, "cat_announce_${currentCategory.id}")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "media_playback_channel",
                "Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing live radio stream notification"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun promoteToForeground(title: String = "No Hands Radio", text: String = "Live Radio Stream") {
        try {
            val pendingIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
            }

            val notification = NotificationCompat.Builder(this, "media_playback_channel")
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e("MusicPlaybackService", "Error starting foreground notification: ${e.message}", e)
        }
    }

    private fun inferMimeType(url: String): String {
        val cleanUrl = url.lowercase().split('?')[0]
        return when {
            cleanUrl.endsWith(".m3u8") || cleanUrl.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
            cleanUrl.endsWith(".aac") || cleanUrl.contains("aac") -> MimeTypes.AUDIO_AAC
            cleanUrl.endsWith(".mp3") || cleanUrl.contains("mp3") -> MimeTypes.AUDIO_MPEG
            cleanUrl.endsWith(".ogg") || cleanUrl.contains("ogg") -> MimeTypes.AUDIO_OGG
            cleanUrl.endsWith(".wav") || cleanUrl.contains("wav") -> MimeTypes.AUDIO_WAV
            else -> MimeTypes.AUDIO_MPEG
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        promoteToForeground()

        // Initialize Text-To-Speech engine for category announcements
        textToSpeech = TextToSpeech(this, this)

        // 1. Configure HTTP Data Source with Cross-Protocol Redirects for Live Radio Streams
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(4000)
            .setReadTimeoutMs(4000)
            .setUserAgent("NoHandsRadio/1.1 Android")

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Fast Load Control: Starts playback after 500ms of buffer for instant station switching
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1500, // minBufferMs
                5000, // maxBufferMs
                500,  // bufferForPlaybackMs (0.5 sec buffer for instant playback start)
                1000  // bufferForPlaybackAfterRebufferMs
            )
            .build()

        // 2. Build ExoPlayer with LoadControl, MediaSourceFactory, Audio Focus, and Wake Lock
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        repository.exoPlayer = player

        // 3. Listener for player states, metadata, ICY metadata, and error recovery
        player.addListener(object : Player.Listener {
            override fun onMetadata(metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    val entry = metadata[i]
                    if (entry is IcyInfo) {
                        val streamTitle = entry.title?.trim().orEmpty()
                        if (streamTitle.isNotBlank()) {
                            var artist = ""
                            var title = streamTitle
                            if (streamTitle.contains(" - ")) {
                                val parts = streamTitle.split(" - ", limit = 2)
                                artist = parts[0].trim()
                                title = parts[1].trim()
                            }
                            RideLogger.log("ICY_METADATA title='$title' artist='$artist'")
                            serviceScope.launch {
                                repository.updateMetadata(title, artist)
                            }
                        }
                    }
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val rawTitle = mediaMetadata.title?.toString() ?: mediaMetadata.displayTitle?.toString()
                val rawArtist = mediaMetadata.artist?.toString() ?: mediaMetadata.subtitle?.toString()

                var title = rawTitle
                var artist = rawArtist

                if (artist.isNullOrBlank() && !title.isNullOrBlank() && title.contains(" - ")) {
                    val parts = title.split(" - ", limit = 2)
                    artist = parts[0].trim()
                    title = parts[1].trim()
                }

                Log.d("MusicPlaybackService", "Metadata updated: title=$title, artist=$artist")
                RideLogger.log("METADATA_UPDATED title='$title' artist='$artist'")
                serviceScope.launch {
                    repository.updateMetadata(title, artist)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isLoading = playbackState == Player.STATE_BUFFERING
                Log.d("MusicPlaybackService", "PlaybackState: $playbackState, isLoading: $isLoading")
                RideLogger.log("PLAYBACK_STATE_CHANGED state=$playbackState isLoading=$isLoading station='${repository.state.value.currentStation?.name}'")
                serviceScope.launch {
                    repository.setLoading(isLoading)
                    if (playbackState == Player.STATE_ENDED) {
                        if (repository.state.value.isPlaying) {
                            player.prepare()
                            player.play()
                        } else {
                            repository.setPlaying(false)
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("MusicPlaybackService", "onIsPlayingChanged: $isPlaying, playWhenReady: ${player.playWhenReady}, state: ${player.playbackState}")
                RideLogger.log("IS_PLAYING_CHANGED isPlaying=$isPlaying station='${repository.state.value.currentStation?.name}'")
                if (isPlaying || !player.playWhenReady) {
                    serviceScope.launch {
                        repository.setPlaying(isPlaying)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicPlaybackService", "ExoPlayer error [${error.errorCodeName}]: ${error.message}", error)
                RideLogger.log("EXOPLAYER_ERROR code='${error.errorCodeName}' message='${error.message}' station='${repository.state.value.currentStation?.name}' -> Auto-advancing")
                serviceScope.launch {
                    repository.nextStation()
                }
            }
        })

        // MediaSession callback to handle Bluetooth media buttons (Play, Pause, Next, Previous)
        val sessionCallback = object : MediaSession.Callback {
            override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                @Suppress("DEPRECATION")
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                    RideLogger.log("BLUETOOTH_MEDIA_BUTTON keyCode=${keyEvent.keyCode}")
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            repository.nextStation()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            if (repository.state.value.bluetoothBackAction == "NEXT_CATEGORY") {
                                serviceScope.launch {
                                    repository.nextCategory()
                                }
                            } else {
                                repository.previousStation()
                            }
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                            serviceScope.launch {
                                repository.togglePlayPause()
                            }
                            return true
                        }
                    }
                }
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }
        }

        val pendingIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .apply {
                if (pendingIntent != null) {
                    setSessionActivity(pendingIntent)
                }
            }
            .build()

        // 4. Collect repository state and drive playback
        serviceScope.launch {
            repository.state.collectLatest { state ->
                state.currentCategory?.let { category ->
                    if (category.id != lastAnnouncedCategoryId) {
                        lastAnnouncedCategoryId = category.id
                        if (state.announceCategories && state.isPlaying && isTtsReady) {
                            Log.d("MusicPlaybackService", "Announcing category name: ${category.name}")
                            RideLogger.log("TTS_CATEGORY_ANNOUNCEMENT category='${category.name}'")
                            textToSpeech?.speak(category.name, TextToSpeech.QUEUE_FLUSH, null, "cat_announce_${category.id}")
                        }
                    }
                }

                state.currentStation?.let { station ->
                    val currentUrl = player.currentMediaItem?.localConfiguration?.uri?.toString()
                    val isErrorOrIdle = player.playerError != null || player.playbackState == Player.STATE_IDLE

                    Log.d("MusicPlaybackService", "State update: station=${station.name}, url=${station.streamUrl}, isPlaying=${state.isPlaying}, currentUrl=$currentUrl, isErrorOrIdle=$isErrorOrIdle")

                    if (state.isPlaying) {
                        promoteToForeground(station.name, state.currentCategory?.name ?: "Live Radio Stream")
                    }

                    if (currentUrl != station.streamUrl || isErrorOrIdle) {
                        RideLogger.log("STATION_CHANGE_INITIATED station='${station.name}' url='${station.streamUrl}' category='${state.currentCategory?.name}'")
                        player.stop()
                        player.clearMediaItems()

                        val metadata = MediaMetadata.Builder()
                            .setTitle(station.name)
                            .setArtist(state.currentCategory?.name ?: "Live Radio Stream")
                            .setStation(station.name)
                            .setIsPlayable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                            .build()

                        val mediaItem = MediaItem.Builder()
                            .setUri(station.streamUrl)
                            .setMimeType(inferMimeType(station.streamUrl))
                            .setMediaMetadata(metadata)
                            .build()

                        player.setMediaItem(mediaItem)
                        player.prepare()
                        if (state.isPlaying) {
                            player.play()
                        }
                    } else {
                        player.playWhenReady = state.isPlaying
                        if (state.isPlaying) {
                            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                                player.prepare()
                            }
                            player.play()
                        } else {
                            player.pause()
                        }
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("MusicPlaybackService", "App swiped away from recent tasks. Stopping playback.")
        RideLogger.log("SERVICE_TASK_REMOVED: User swiped app away from recent tasks.")
        serviceScope.launch {
            repository.setPlaying(false)
        }
        player.stop()
        player.clearMediaItems()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        repository.exoPlayer = null
        textToSpeech?.run {
            stop()
            shutdown()
        }
        textToSpeech = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}