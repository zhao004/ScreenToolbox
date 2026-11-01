package com.zhao.screentoolbox.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 应用统一色板。
 *
 * Compose 和系统悬浮窗共用这些基础色，避免不同界面维护重复色值。
 */
internal object AppPalette {
    object Light {
        val primary = Color(0xFF2563EB)
        val onPrimary = Color(0xFFFFFFFF)
        val primaryContainer = Color(0xFFDBEAFE)
        val onPrimaryContainer = Color(0xFF172554)
        val inversePrimary = Color(0xFF93B4FF)

        val secondary = Color(0xFF475569)
        val onSecondary = Color(0xFFFFFFFF)
        val secondaryContainer = Color(0xFFE2E8F0)
        val onSecondaryContainer = Color(0xFF0F172A)

        val tertiary = Color(0xFF0E7490)
        val onTertiary = Color(0xFFFFFFFF)
        val tertiaryContainer = Color(0xFFCFFAFE)
        val onTertiaryContainer = Color(0xFF164E63)

        val error = Color(0xFFB91C1C)
        val onError = Color(0xFFFFFFFF)
        val errorContainer = Color(0xFFFEE2E2)
        val onErrorContainer = Color(0xFF7F1D1D)

        val background = Color(0xFFF8FAFC)
        val onBackground = Color(0xFF0F172A)
        val surface = Color(0xFFFFFFFF)
        val onSurface = Color(0xFF0F172A)
        val surfaceVariant = Color(0xFFF1F5F9)
        val onSurfaceVariant = Color(0xFF475569)
        val surfaceTint = primary
        val inverseSurface = Color(0xFF1E293B)
        val inverseOnSurface = Color(0xFFF1F5F9)
        val outline = Color(0xFF94A3B8)
        val outlineVariant = Color(0xFFE2E8F0)
        val scrim = Color(0xFF000000)
        val surfaceBright = Color(0xFFFFFFFF)
        val surfaceContainer = Color(0xFFF1F5F9)
        val surfaceContainerHigh = Color(0xFFE8EEF5)
        val surfaceContainerHighest = Color(0xFFDDE5EE)
        val surfaceContainerLow = Color(0xFFF8FAFC)
        val surfaceContainerLowest = Color(0xFFFFFFFF)
        val surfaceDim = Color(0xFFD8E0EA)
    }

    object Dark {
        val primary = Color(0xFF93B4FF)
        val onPrimary = Color(0xFF0A2A66)
        val primaryContainer = Color(0xFF1E3A8A)
        val onPrimaryContainer = Color(0xFFDBEAFE)
        val inversePrimary = Color(0xFF2563EB)

        val secondary = Color(0xFFCBD5E1)
        val onSecondary = Color(0xFF0F172A)
        val secondaryContainer = Color(0xFF334155)
        val onSecondaryContainer = Color(0xFFE2E8F0)

        val tertiary = Color(0xFF67E8F9)
        val onTertiary = Color(0xFF164E63)
        val tertiaryContainer = Color(0xFF155E75)
        val onTertiaryContainer = Color(0xFFCFFAFE)

        val error = Color(0xFFF87171)
        val onError = Color(0xFF450A0A)
        val errorContainer = Color(0xFF991B1B)
        val onErrorContainer = Color(0xFFFEE2E2)

        val background = Color(0xFF0B1120)
        val onBackground = Color(0xFFF8FAFC)
        val surface = Color(0xFF111827)
        val onSurface = Color(0xFFF8FAFC)
        val surfaceVariant = Color(0xFF182235)
        val onSurfaceVariant = Color(0xFFA8B3C7)
        val surfaceTint = primary
        val inverseSurface = Color(0xFFF1F5F9)
        val inverseOnSurface = Color(0xFF1E293B)
        val outline = Color(0xFF64748B)
        val outlineVariant = Color(0xFF243047)
        val scrim = Color(0xFF000000)
        val surfaceBright = Color(0xFF273449)
        val surfaceContainer = Color(0xFF111827)
        val surfaceContainerHigh = Color(0xFF182235)
        val surfaceContainerHighest = Color(0xFF253247)
        val surfaceContainerLow = Color(0xFF0B1120)
        val surfaceContainerLowest = Color(0xFF070D18)
        val surfaceDim = Color(0xFF0B1120)
    }
}

/**
 * Material 3 之外的业务状态颜色。
 *
 * 这些颜色随明暗主题切换，调用方不应直接使用基础绿色或琥珀色。
 */
@Immutable
internal data class AppSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

internal val LightSemanticColors = AppSemanticColors(
    success = Color(0xFF15803D),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFDCFCE7),
    onSuccessContainer = Color(0xFF14532D),
    warning = Color(0xFFB45309),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF78350F),
)

internal val DarkSemanticColors = AppSemanticColors(
    success = Color(0xFF4ADE80),
    onSuccess = Color(0xFF052E16),
    successContainer = Color(0xFF166534),
    onSuccessContainer = Color(0xFFDCFCE7),
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF451A03),
    warningContainer = Color(0xFF92400E),
    onWarningContainer = Color(0xFFFEF3C7),
)

internal val LocalAppSemanticColors = staticCompositionLocalOf {
    LightSemanticColors
}
