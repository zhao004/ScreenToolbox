package com.zhao.screentoolbox.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.zhao.screentoolbox.overlay.editor.resolveOverlayEditorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class AppThemeColorTest {
    @Test
    fun lightThemeTextPairsMeetContrastRequirement() {
        assertSchemeContrast(LightColorScheme, LightSemanticColors)
    }

    @Test
    fun darkThemeTextPairsMeetContrastRequirement() {
        assertSchemeContrast(DarkColorScheme, DarkSemanticColors)
    }

    @Test
    fun overlayEditorPaletteUsesSharedThemeColors() {
        val light = resolveOverlayEditorPalette(isDark = false)
        val dark = resolveOverlayEditorPalette(isDark = true)

        assertEquals(LightColorScheme.primary.toArgb(), light.primary)
        assertEquals(LightColorScheme.onSurface.toArgb(), light.title)
        assertEquals(LightColorScheme.primaryContainer.toArgb(), light.primaryContainer)
        assertEquals(DarkColorScheme.primary.toArgb(), dark.primary)
        assertEquals(DarkColorScheme.onSurface.toArgb(), dark.title)
        assertEquals(DarkColorScheme.primaryContainer.toArgb(), dark.primaryContainer)
    }

    private fun assertSchemeContrast(
        scheme: ColorScheme,
        semanticColors: AppSemanticColors,
    ) {
        assertContrast("primary", scheme.onPrimary, scheme.primary)
        assertContrast("primary container", scheme.onPrimaryContainer, scheme.primaryContainer)
        assertContrast("secondary", scheme.onSecondary, scheme.secondary)
        assertContrast("secondary container", scheme.onSecondaryContainer, scheme.secondaryContainer)
        assertContrast("tertiary", scheme.onTertiary, scheme.tertiary)
        assertContrast("tertiary container", scheme.onTertiaryContainer, scheme.tertiaryContainer)
        assertContrast("error", scheme.onError, scheme.error)
        assertContrast("error container", scheme.onErrorContainer, scheme.errorContainer)
        assertContrast("background", scheme.onBackground, scheme.background)
        assertContrast("surface", scheme.onSurface, scheme.surface)
        assertContrast("surface variant", scheme.onSurfaceVariant, scheme.surface)
        assertContrast("success", semanticColors.onSuccess, semanticColors.success)
        assertContrast(
            "success container",
            semanticColors.onSuccessContainer,
            semanticColors.successContainer,
        )
        assertContrast("warning", semanticColors.onWarning, semanticColors.warning)
        assertContrast(
            "warning container",
            semanticColors.onWarningContainer,
            semanticColors.warningContainer,
        )
    }

    private fun assertContrast(name: String, foreground: Color, background: Color) {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        val ratio = (max(foregroundLuminance, backgroundLuminance) + 0.05f) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05f)
        assertTrue("$name contrast ratio was $ratio", ratio >= MIN_TEXT_CONTRAST)
    }

    private companion object {
        const val MIN_TEXT_CONTRAST = 4.5f
    }
}
