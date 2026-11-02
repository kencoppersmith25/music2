package com.coppersmith.music2.cast

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaRouteSelector
import com.coppersmith.music2.R
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

@Composable
fun GoogleCastIconButton(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    AndroidView(
        factory = { ctx ->
            val themedContext = ContextThemeWrapper(ctx, R.style.Theme_MyApplication)
            MediaRouteButton(themedContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                try {
                    val selector = CastContext.getSharedInstance(ctx).mergedSelector
                    if (selector != null) {
                        setRouteSelector(selector)
                    }
                } catch (_: Exception) {
                    val fallbackSelector = MediaRouteSelector.Builder()
                        .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                        .build()
                    setRouteSelector(fallbackSelector)
                }
                setAlwaysVisible(true)
                try {
                    CastButtonFactory.setUpMediaRouteButton(ctx, this)
                } catch (_: Exception) {}
            }
        },
        modifier = modifier.size(48.dp)
    )
}