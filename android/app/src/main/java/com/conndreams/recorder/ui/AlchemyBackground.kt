package com.conndreams.recorder.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conndreams.recorder.R
import com.conndreams.recorder.ui.theme.GoldLight
import com.conndreams.recorder.ui.theme.Parchment
import com.conndreams.recorder.ui.theme.Violet

/**
 * Backdrop matching the Electron app's atmosphere: radial glows, slowly-rotating sigil
 * watermark, and drifting alchemical glyphs at very low opacity.
 */
@Composable
fun AlchemyBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x8C161037), Color.Transparent),
                            center = Offset(size.width * 0.5f, -size.height * 0.05f),
                            radius = size.height * 0.45f,
                        ),
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x2E3C1E08), Color.Transparent),
                            center = Offset(size.width * 0.85f, size.height * 0.9f),
                            radius = size.height * 0.6f,
                        ),
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x590E0C28), Color.Transparent),
                            center = Offset(size.width * 0.1f, size.height * 0.6f),
                            radius = size.height * 0.4f,
                        ),
                    )
                },
        )

        val sigilTransition = rememberInfiniteTransition(label = "sigil")
        val angle by sigilTransition.animateFloat(
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

        DriftingGlyphs()

        content()
    }
}

@Composable
private fun DriftingGlyphs() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Glyph("☉", 0.85f, 0.10f, 44.sp, GoldLight, 0.07f, 32_000, 14.dp, (-18).dp)
        Glyph("☽", 0.08f, 0.78f, 36.sp, Parchment, 0.06f, 38_000, (-10).dp, (-12).dp)
        Glyph("☿", 0.92f, 0.55f, 30.sp, Violet, 0.07f, 22_000, 8.dp, (-10).dp)
        Glyph("♄", 0.05f, 0.22f, 38.sp, GoldLight, 0.05f, 28_000, 12.dp, 8.dp)
        Glyph("♃", 0.50f, 0.92f, 28.sp, Parchment, 0.06f, 26_000, (-8).dp, (-14).dp)
        Glyph("♀", 0.78f, 0.86f, 32.sp, Violet, 0.05f, 30_000, (-10).dp, (-8).dp)
        Glyph("♂", 0.16f, 0.50f, 28.sp, GoldLight, 0.05f, 34_000, 9.dp, (-11).dp)
    }
}

@Composable
private fun BoxWithConstraintsScope.Glyph(
    symbol: String,
    relX: Float,
    relY: Float,
    fontSize: TextUnit,
    color: Color,
    alpha: Float,
    durationMs: Int,
    driftX: Dp,
    driftY: Dp,
) {
    val transition = rememberInfiniteTransition(label = "glyph-$symbol")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glyph-$symbol-phase",
    )

    Text(
        text = symbol,
        color = color,
        fontSize = fontSize,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Light,
        modifier = Modifier
            .offset(x = maxWidth * relX + driftX * phase, y = maxHeight * relY + driftY * phase)
            .alpha(alpha),
    )
}
