package com.conndreams.recorder.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.conndreams.recorder.R

/**
 * Backdrop layer matching the Electron app's atmosphere:
 * - radial violet haze at top
 * - faint ember warmth bottom-right
 * - void depth on the left
 * - very faint slowly-rotating sigil watermark behind everything
 */
@Composable
fun AlchemyBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize()) {
        // Base void
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // --glow-top: radial violet haze at top
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x8C161037),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.5f, -size.height * 0.05f),
                            radius = size.height * 0.45f,
                        ),
                    )
                    // --glow-br: faint ember warmth bottom-right
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x2E3C1E08),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.9f),
                            radius = size.height * 0.6f,
                        ),
                    )
                    // --glow-left: void depth on left
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x590E0C28),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.1f, size.height * 0.6f),
                            radius = size.height * 0.4f,
                        ),
                    )
                },
        )

        // Slowly rotating sigil watermark
        val transition = rememberInfiniteTransition(label = "sigil")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 240_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "sigil-angle",
        )

        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(640.dp)
                .alpha(0.05f)
                .rotate(angle),
        )

        content()
    }
}
