package com.zhao.screentoolbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

internal val LightColorScheme = lightColorScheme(
    primary = AppPalette.Light.primary,
    onPrimary = AppPalette.Light.onPrimary,
    primaryContainer = AppPalette.Light.primaryContainer,
    onPrimaryContainer = AppPalette.Light.onPrimaryContainer,
    inversePrimary = AppPalette.Light.inversePrimary,
    secondary = AppPalette.Light.secondary,
    onSecondary = AppPalette.Light.onSecondary,
    secondaryContainer = AppPalette.Light.secondaryContainer,
    onSecondaryContainer = AppPalette.Light.onSecondaryContainer,
    tertiary = AppPalette.Light.tertiary,
    onTertiary = AppPalette.Light.onTertiary,
    tertiaryContainer = AppPalette.Light.tertiaryContainer,
    onTertiaryContainer = AppPalette.Light.onTertiaryContainer,
    error = AppPalette.Light.error,
    onError = AppPalette.Light.onError,
    errorContainer = AppPalette.Light.errorContainer,
    onErrorContainer = AppPalette.Light.onErrorContainer,
    background = AppPalette.Light.background,
    onBackground = AppPalette.Light.onBackground,
    surface = AppPalette.Light.surface,
    onSurface = AppPalette.Light.onSurface,
    surfaceVariant = AppPalette.Light.surfaceVariant,
    onSurfaceVariant = AppPalette.Light.onSurfaceVariant,
    surfaceTint = AppPalette.Light.surfaceTint,
    inverseSurface = AppPalette.Light.inverseSurface,
    inverseOnSurface = AppPalette.Light.inverseOnSurface,
    outline = AppPalette.Light.outline,
    outlineVariant = AppPalette.Light.outlineVariant,
    scrim = AppPalette.Light.scrim,
    surfaceBright = AppPalette.Light.surfaceBright,
    surfaceContainer = AppPalette.Light.surfaceContainer,
    surfaceContainerHigh = AppPalette.Light.surfaceContainerHigh,
    surfaceContainerHighest = AppPalette.Light.surfaceContainerHighest,
    surfaceContainerLow = AppPalette.Light.surfaceContainerLow,
    surfaceContainerLowest = AppPalette.Light.surfaceContainerLowest,
    surfaceDim = AppPalette.Light.surfaceDim,
)

internal val DarkColorScheme = darkColorScheme(
    primary = AppPalette.Dark.primary,
    onPrimary = AppPalette.Dark.onPrimary,
    primaryContainer = AppPalette.Dark.primaryContainer,
    onPrimaryContainer = AppPalette.Dark.onPrimaryContainer,
    inversePrimary = AppPalette.Dark.inversePrimary,
    secondary = AppPalette.Dark.secondary,
    onSecondary = AppPalette.Dark.onSecondary,
    secondaryContainer = AppPalette.Dark.secondaryContainer,
    onSecondaryContainer = AppPalette.Dark.onSecondaryContainer,
    tertiary = AppPalette.Dark.tertiary,
    onTertiary = AppPalette.Dark.onTertiary,
    tertiaryContainer = AppPalette.Dark.tertiaryContainer,
    onTertiaryContainer = AppPalette.Dark.onTertiaryContainer,
    error = AppPalette.Dark.error,
    onError = AppPalette.Dark.onError,
    errorContainer = AppPalette.Dark.errorContainer,
    onErrorContainer = AppPalette.Dark.onErrorContainer,
    background = AppPalette.Dark.background,
    onBackground = AppPalette.Dark.onBackground,
    surface = AppPalette.Dark.surface,
    onSurface = AppPalette.Dark.onSurface,
    surfaceVariant = AppPalette.Dark.surfaceVariant,
    onSurfaceVariant = AppPalette.Dark.onSurfaceVariant,
    surfaceTint = AppPalette.Dark.surfaceTint,
    inverseSurface = AppPalette.Dark.inverseSurface,
    inverseOnSurface = AppPalette.Dark.inverseOnSurface,
    outline = AppPalette.Dark.outline,
    outlineVariant = AppPalette.Dark.outlineVariant,
    scrim = AppPalette.Dark.scrim,
    surfaceBright = AppPalette.Dark.surfaceBright,
    surfaceContainer = AppPalette.Dark.surfaceContainer,
    surfaceContainerHigh = AppPalette.Dark.surfaceContainerHigh,
    surfaceContainerHighest = AppPalette.Dark.surfaceContainerHighest,
    surfaceContainerLow = AppPalette.Dark.surfaceContainerLow,
    surfaceContainerLowest = AppPalette.Dark.surfaceContainerLowest,
    surfaceDim = AppPalette.Dark.surfaceDim,
)

internal val MaterialTheme.appSemanticColors: AppSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppSemanticColors.current

/**
 * 为应用提供统一的品牌色和业务状态色。
 *
 * 主题跟随系统明暗模式，也可由测试或预览显式指定。
 */
@Composable
fun ScreenToolboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppSemanticColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
