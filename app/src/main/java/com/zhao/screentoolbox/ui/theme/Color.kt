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
        val primary = Color(0xFF1976D2)
        val onPrimary = Color(0xFFFFFFFF)
        val primaryContainer = Color(0xFFE8F1FF)
        val onPrimaryContainer = Color(0xFF0D3C78)
        val inversePrimary = Color(0xFFA9C7FF)

        val secondary = Color(0xFF52606D)
        val onSecondary = Color(0xFFFFFFFF)
        val secondaryContainer = Color(0xFFE8EEF5)
        val onSecondaryContainer = Color(0xFF182230)

        val tertiary = Color(0xFF006A60)
        val onTertiary = Color(0xFFFFFFFF)
        val tertiaryContainer = Color(0xFFC8F3EC)
        val onTertiaryContainer = Color(0xFF002F2B)

        val error = Color(0xFFB3261E)
        val onError = Color(0xFFFFFFFF)
        val errorContainer = Color(0xFFFEE2E2)
        val onErrorContainer = Color(0xFF680003)

        val background = Color(0xFFF5F7FA)
        val onBackground = Color(0xFF1F2937)
        val surface = Color(0xFFFFFFFF)
        val onSurface = Color(0xFF1F2937)
        val surfaceVariant = Color(0xFFEDF1F5)
        val onSurfaceVariant = Color(0xFF64748B)
        val surfaceTint = primary
        val inverseSurface = Color(0xFF263238)
        val inverseOnSurface = Color(0xFFF4F6F8)
        val outline = Color(0xFF8492A6)
        val outlineVariant = Color(0xFFDCE3EA)
        val scrim = Color(0xFF000000)
        val surfaceBright = Color(0xFFFFFFFF)
        val surfaceContainer = Color(0xFFF0F3F7)
        val surfaceContainerHigh = Color(0xFFE8EDF3)
        val surfaceContainerHighest = Color(0xFFDFE6EE)
        val surfaceContainerLow = Color(0xFFF7F9FC)
        val surfaceContainerLowest = Color(0xFFFFFFFF)
        val surfaceDim = Color(0xFFD9E0E8)
    }

    object Dark {
        val primary = Color(0xFF8CC2FF)
        val onPrimary = Color(0xFF003258)
        val primaryContainer = Color(0xFF00497D)
        val onPrimaryContainer = Color(0xFFD1E4FF)
        val inversePrimary = Color(0xFF1976D2)

        val secondary = Color(0xFFBEC8D8)
        val onSecondary = Color(0xFF28313E)
        val secondaryContainer = Color(0xFF3E4858)
        val onSecondaryContainer = Color(0xFFDAE3F3)

        val tertiary = Color(0xFF72D8CA)
        val onTertiary = Color(0xFF003731)
        val tertiaryContainer = Color(0xFF005047)
        val onTertiaryContainer = Color(0xFF8EF4E6)

        val error = Color(0xFFFFB4AB)
        val onError = Color(0xFF690005)
        val errorContainer = Color(0xFF93000A)
        val onErrorContainer = Color(0xFFFFDAD6)

        val background = Color(0xFF0F172A)
        val onBackground = Color(0xFFF1F5F9)
        val surface = Color(0xFF141D2E)
        val onSurface = Color(0xFFF1F5F9)
        val surfaceVariant = Color(0xFF202B3C)
        val onSurfaceVariant = Color(0xFFB8C4D6)
        val surfaceTint = primary
        val inverseSurface = Color(0xFFE2E8F0)
        val inverseOnSurface = Color(0xFF1A2433)
        val outline = Color(0xFF8994A6)
        val outlineVariant = Color(0xFF2B3749)
        val scrim = Color(0xFF000000)
        val surfaceBright = Color(0xFF2A374A)
        val surfaceContainer = Color(0xFF182235)
        val surfaceContainerHigh = Color(0xFF1D293C)
        val surfaceContainerHighest = Color(0xFF253247)
        val surfaceContainerLow = Color(0xFF111B2B)
        val surfaceContainerLowest = Color(0xFF0A111E)
        val surfaceDim = Color(0xFF0C1321)
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
