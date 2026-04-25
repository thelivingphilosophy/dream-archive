package com.conndreams.recorder.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AlchemyDark = darkColorScheme(
    primary = Gold,
    onPrimary = ParchmentBright,
    primaryContainer = GoldDark,
    onPrimaryContainer = GoldLight,

    secondary = Violet,
    onSecondary = ParchmentBright,
    secondaryContainer = DeepViolet,
    onSecondaryContainer = Parchment,

    tertiary = GoldWarm,
    onTertiary = Void,

    background = Void,
    onBackground = Parchment,

    surface = Surface1,
    onSurface = Parchment,
    surfaceVariant = Surface2,
    onSurfaceVariant = VioletGrey,

    outline = Border2,
    outlineVariant = Border1,

    error = EmberOrange,
    onError = ParchmentBright,
)

@Composable
fun ConnDreamsTheme(content: @Composable () -> Unit) {
    val colorScheme = AlchemyDark
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = false
            insets.isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ConnDreamsTypography,
        content = content,
    )
}
