package com.zhao.screentoolbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = SuccessGreen,
    onSecondary = Color.White,
    error = DangerRed,
    background = AppBackground,
    onBackground = Ink,
    surface = AppSurface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = MutedInk,
    outline = AppOutline,
)

private val DarkScheme = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Ink,
    secondary = SuccessGreen,
    error = DangerRed,
    background = DarkBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = DarkSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF182235),
    onSurfaceVariant = Color(0xFFA8B3C7),
    outline = DarkOutline,
)

@Composable
fun ScreenToolboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
