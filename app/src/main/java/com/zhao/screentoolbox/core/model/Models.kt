package com.zhao.screentoolbox.core.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 悬浮窗类型决定窗口视图、触摸策略和可编辑参数。 */
enum class OverlayType {
    BURN_MASK,
    TOUCH_BLOCK,
    WATERMARK,
}

/** 水印布局支持单条定位和全屏平铺。 */
enum class WatermarkMode {
    SINGLE,
    TILED,
}

/**
 * 以屏幕可用宽高归一化保存的矩形。
 *
 * 归一化坐标避免分辨率和方向变化后窗口直接错位，所有持久化入口都必须经过
 * [sanitized] 或 [clamped] 校验。
 */
data class NormalizedRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    /** 将尺寸限制到屏幕范围内，并保证窗口至少保留一个有效像素。 */
    fun sanitized(): NormalizedRect {
        val safeWidth = width.coerceIn(0.001f, 1f)
        val safeHeight = height.coerceIn(0.001f, 1f)
        return NormalizedRect(
            x = x.coerceIn(0f, 1f - safeWidth),
            y = y.coerceIn(0f, 1f - safeHeight),
            width = safeWidth,
            height = safeHeight,
        )
    }

    /** 将矩形夹取到指定像素边界内，并遵守最小窗口尺寸。 */
    fun clamped(screenWidth: Int, screenHeight: Int, minWidthPx: Int, minHeightPx: Int): NormalizedRect {
        if (screenWidth <= 0 || screenHeight <= 0) return sanitized()
        val minWidthRatio = min(1f, minWidthPx.toFloat() / screenWidth)
        val minHeightRatio = min(1f, minHeightPx.toFloat() / screenHeight)
        val safeWidth = width.coerceIn(minWidthRatio, 1f)
        val safeHeight = height.coerceIn(minHeightRatio, 1f)
        return NormalizedRect(
            x = x.coerceIn(0f, 1f - safeWidth),
            y = y.coerceIn(0f, 1f - safeHeight),
            width = safeWidth,
            height = safeHeight,
        )
    }

    companion object {
        val FullScreen = NormalizedRect(0f, 0f, 1f, 1f)

        /** 比较像素坐标中的平移量，用于悬浮窗手势判定。 */
        fun nearlyEquals(first: Float, second: Float, epsilon: Float = 0.0001f): Boolean =
            abs(first - second) <= epsilon
    }
}

/** 所有可持久化悬浮窗共享的配置契约。 */
sealed interface OverlayConfig {
    val id: String
    val name: String
    val enabled: Boolean
    val rect: NormalizedRect
    val type: OverlayType
}

data class BurnMaskConfig(
    override val id: String,
    override val name: String,
    override val enabled: Boolean,
    override val rect: NormalizedRect,
    val colorArgb: Long = 0xFF000000,
    val cornerRadiusDp: Float = 12f,
    val alpha: Float = 1f,
) : OverlayConfig {
    override val type: OverlayType = OverlayType.BURN_MASK
}

data class TouchBlockConfig(
    override val id: String,
    override val name: String,
    override val enabled: Boolean,
    override val rect: NormalizedRect,
    val boundaryVisible: Boolean = true,
    val alpha: Float = 0f,
) : OverlayConfig {
    override val type: OverlayType = OverlayType.TOUCH_BLOCK
}

data class WatermarkConfig(
    override val id: String,
    override val name: String,
    override val enabled: Boolean,
    override val rect: NormalizedRect = NormalizedRect.FullScreen,
    val text: String = "屏幕工具箱",
    val textSizeSp: Float = 24f,
    val fontWeight: Int = 500,
    val colorArgb: Long = 0xFFFFFFFF,
    val alpha: Float = 0.35f,
    val rotationDegrees: Float = -20f,
    val mode: WatermarkMode = WatermarkMode.TILED,
    val tileSpacingDp: Float = 96f,
    val positionXRatio: Float = 0.5f,
    val positionYRatio: Float = 0.5f,
) : OverlayConfig {
    override val type: OverlayType = OverlayType.WATERMARK
}

data class AppRule(
    val id: Long = 0,
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val burnMaskEnabled: Boolean = true,
    val touchBlockEnabled: Boolean = true,
    val watermarkEnabled: Boolean = true,
    val configIds: Set<String> = emptySet(),
)

/** 全局规则复用应用规则表，使用不可作为 Android 包名的保留键。 */
const val GLOBAL_RULE_PACKAGE = "__screen_toolbox_global__"

/** 全局规则在界面和数据库中的固定名称。 */
const val GLOBAL_RULE_DISPLAY_NAME = "全局应用"

/** 判断规则是否代表对所有前台应用生效的全局规则。 */
fun AppRule.isGlobalRule(): Boolean = packageName == GLOBAL_RULE_PACKAGE

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
)

data class AppSettings(
    val serviceEnabled: Boolean = false,
    val autoStartOnBoot: Boolean = false,
)

data class PermissionSnapshot(
    val overlayGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val notificationGranted: Boolean = false,
)

data class OverlayRuleResolution(
    val activeConfigs: List<OverlayConfig>,
    val matchedRulePackage: String? = null,
    val ruleControlledTypes: Set<OverlayType> = emptySet(),
)

fun OverlayConfig.withRect(rect: NormalizedRect): OverlayConfig = when (this) {
    is BurnMaskConfig -> copy(rect = rect)
    is TouchBlockConfig -> copy(rect = rect)
    is WatermarkConfig -> copy(rect = rect)
}

fun normalizedRectFromPixels(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    screenWidth: Int,
    screenHeight: Int,
    minWidthPx: Int,
    minHeightPx: Int,
): NormalizedRect {
    if (screenWidth <= 0 || screenHeight <= 0) return NormalizedRect.FullScreen
    val safeWidth = max(minWidthPx, width).coerceAtMost(screenWidth)
    val safeHeight = max(minHeightPx, height).coerceAtMost(screenHeight)
    return NormalizedRect(
        x = x.toFloat() / screenWidth,
        y = y.toFloat() / screenHeight,
        width = safeWidth.toFloat() / screenWidth,
        height = safeHeight.toFloat() / screenHeight,
    ).clamped(screenWidth, screenHeight, minWidthPx, minHeightPx)
}
