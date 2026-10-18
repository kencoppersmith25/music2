package com.coppersmith.music2.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay


@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onPreviousCategoryClick: () -> Unit,
    onPreviousStationClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextStationClick: () -> Unit,
    onNextCategoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {
        DirectionButton(
            symbol = "<<",
            onClick =
                onPreviousCategoryClick
        )

        DirectionButton(
            symbol = "<",
            onClick =
                onPreviousStationClick
        )

        Button(
            onClick = onPlayPauseClick,
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor =
                    if (isPlaying) {
                        Color(0xFFD71920)
                    } else {
                        Color(0xFF1D9A50)
                    }
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text =
                    if (isPlaying) "■" else "▶",
                color = Color.Black,
                fontSize =
                    if (isPlaying) {
                        38.sp
                    } else {
                        35.sp
                    },
                fontWeight = FontWeight.Bold
            )
        }

        DirectionButton(
            symbol = ">",
            onClick =
                onNextStationClick
        )

        DirectionButton(
            symbol = ">>",
            onClick =
                onNextCategoryClick
        )
    }
}

@Composable
fun DirectionButton(
    symbol: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .width(44.dp)
            .height(96.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = symbol,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 35.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            softWrap = false,
            maxLines = 1
        )
    }
}

@Composable
fun MiniVuMeter(
    isPlaying: Boolean
) {
    var animationFrame by remember {
        mutableStateOf(0)
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            animationFrame = 0
            return@LaunchedEffect
        }

        while (true) {
            delay(150)
            animationFrame =
                (animationFrame + 1) % 8
        }
    }

    val patterns = listOf(
        listOf(
            0.18f,
            0.18f,
            0.18f,
            0.18f,
            0.18f
        ),
        listOf(
            0.30f,
            0.55f,
            0.85f,
            0.45f,
            0.25f
        ),
        listOf(
            0.55f,
            0.30f,
            0.65f,
            0.90f,
            0.40f
        ),
        listOf(
            0.25f,
            0.75f,
            0.45f,
            0.65f,
            0.85f
        ),
        listOf(
            0.70f,
            0.45f,
            0.90f,
            0.35f,
            0.60f
        ),
        listOf(
            0.40f,
            0.85f,
            0.55f,
            0.75f,
            0.30f
        ),
        listOf(
            0.80f,
            0.35f,
            0.70f,
            0.50f,
            0.90f
        ),
        listOf(
            0.45f,
            0.65f,
            0.30f,
            0.85f,
            0.55f
        )
    )

    val currentPattern =
        patterns[animationFrame]

    val vuBarColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = Modifier
            .width(34.dp)
            .height(18.dp)
    ) {
        val barCount =
            currentPattern.size

        val gap =
            2.dp.toPx()

        val availableWidth =
            size.width -
                    gap * (barCount - 1)

        val barWidth =
            availableWidth / barCount

        currentPattern.forEachIndexed {
                index,
                heightFraction ->

            // SAFETY GUARD: Prevent NaN or Infinite heights from crashing the UI
            val safeFraction = if (heightFraction.isNaN() || heightFraction.isInfinite()) 0.18f else heightFraction

            val barHeight =
                size.height * safeFraction

            val xOffset = index * (barWidth + gap)
            val yOffset = size.height - barHeight

            // SAFETY GUARD: Prevent drawing if coordinates are invalid
            if (!xOffset.isNaN() && !xOffset.isInfinite() && !yOffset.isNaN() && !yOffset.isInfinite()) {
                drawRoundRect(
                    color = vuBarColor,
                    topLeft = Offset(
                        x = xOffset,
                        y = yOffset
                    ),
                    size = Size(
                        width = barWidth,
                        height = barHeight
                    ),
                    cornerRadius =
                        CornerRadius(
                            x = 1.dp.toPx(),
                            y = 1.dp.toPx()
                        ),
                    style = Fill
                )
            }
        }
    }
}