package com.zhao.screentoolbox.overlay.editor

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import com.zhao.screentoolbox.ui.theme.AppPalette

/**
 * 系统悬浮编辑面板使用的 Android View 色值。
 *
 * 面板不在 Compose 树中，因此在此将共享主题色转换为颜色整数。
 */
internal data class OverlayEditorPalette(
    @ColorInt val container: Int,
    @ColorInt val containerBorder: Int,
    @ColorInt val title: Int,
    @ColorInt val body: Int,
    @ColorInt val primary: Int,
    @ColorInt val onPrimary: Int,
    @ColorInt val primaryContainer: Int,
    @ColorInt val onPrimaryContainer: Int,
    @ColorInt val neutralContainer: Int,
    @ColorInt val onNeutralContainer: Int,
    @ColorInt val outline: Int,
    @ColorInt val outlineVariant: Int,
)

internal fun resolveOverlayEditorPalette(isDark: Boolean): OverlayEditorPalette {
    val containerAlpha = if (isDark) 0.96f else 0.98f
    fun <T> select(light: T, dark: T): T = if (isDark) dark else light

    return OverlayEditorPalette(
        container = select(AppPalette.Light.surface, AppPalette.Dark.surface)
            .copy(alpha = containerAlpha)
            .toArgb(),
        containerBorder = select(
            AppPalette.Light.outlineVariant,
            AppPalette.Dark.outlineVariant,
        ).toArgb(),
        title = select(AppPalette.Light.onSurface, AppPalette.Dark.onSurface).toArgb(),
        body = select(AppPalette.Light.onSurfaceVariant, AppPalette.Dark.onSurfaceVariant).toArgb(),
        primary = select(AppPalette.Light.primary, AppPalette.Dark.primary).toArgb(),
        onPrimary = select(AppPalette.Light.onPrimary, AppPalette.Dark.onPrimary).toArgb(),
        primaryContainer = select(
            AppPalette.Light.primaryContainer,
            AppPalette.Dark.primaryContainer,
        ).toArgb(),
        onPrimaryContainer = select(
            AppPalette.Light.onPrimaryContainer,
            AppPalette.Dark.onPrimaryContainer,
        ).toArgb(),
        neutralContainer = select(
            AppPalette.Light.secondaryContainer,
            AppPalette.Dark.secondaryContainer,
        ).toArgb(),
        onNeutralContainer = select(
            AppPalette.Light.onSecondaryContainer,
            AppPalette.Dark.onSecondaryContainer,
        ).toArgb(),
        outline = select(AppPalette.Light.outline, AppPalette.Dark.outline).toArgb(),
        outlineVariant = select(
            AppPalette.Light.outlineVariant,
            AppPalette.Dark.outlineVariant,
        ).toArgb(),
    )
}
