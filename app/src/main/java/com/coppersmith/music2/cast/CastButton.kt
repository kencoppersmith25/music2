package com.coppersmith.music2.cast

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener

@Composable
fun GoogleCastIconButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isConnected by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val castContext = try {
            CastContext.getSharedInstance(context)
        } catch (e: Exception) {
            null
        }

        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                isConnected = true
            }
            override fun onSessionStartFailed(session: CastSession, error: Int) {
                isConnected = false
            }
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionEnded(session: CastSession, error: Int) {
                isConnected = false
            }
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                isConnected = true
            }
            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                isConnected = false
            }
            override fun onSessionSuspended(session: CastSession, reason: Int) {
                isConnected = false
            }
        }

        castContext?.sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        isConnected = castContext?.sessionManager?.currentCastSession?.isConnected == true

        onDispose {
            castContext?.sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
        }
    }

    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
            contentDescription = "Google Cast Audio",
            tint = if (isConnected) Color(0xFF81C784) else Color.White,
            modifier = Modifier.size(24.dp)
        )

        AndroidView(
            factory = { ctx ->
                val themedCtx = android.view.ContextThemeWrapper(ctx, com.coppersmith.music2.R.style.Theme_MyApplication)
                MediaRouteButton(themedCtx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val selector = MediaRouteSelector.Builder()
                        .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                        .build()
                    setRouteSelector(selector)
                    try {
                        CastButtonFactory.setUpMediaRouteButton(ctx, this)
                    } catch (_: Exception) {}
                }
            },
            modifier = Modifier
                .matchParentSize()
                .alpha(0.01f)
        )
    }
}