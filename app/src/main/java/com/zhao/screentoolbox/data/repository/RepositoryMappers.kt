package com.zhao.screentoolbox.data.repository

import com.zhao.screentoolbox.core.model.AppRule
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.OverlayType
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.core.model.WatermarkConfig
import com.zhao.screentoolbox.core.model.WatermarkMode
import com.zhao.screentoolbox.data.database.AppRuleWithConfigIds
import com.zhao.screentoolbox.data.database.BurnMaskStyleEntity
import com.zhao.screentoolbox.data.database.OverlayConfigEntity
import com.zhao.screentoolbox.data.database.OverlayConfigWithDetails
import com.zhao.screentoolbox.data.database.OverlayRegionEntity
import com.zhao.screentoolbox.data.database.TouchBlockStyleEntity
import com.zhao.screentoolbox.data.database.WatermarkStyleEntity

private const val DEFAULT_BURN_MASK_ALPHA = 1f
private const val DEFAULT_TOUCH_BLOCK_ALPHA = 0f

internal fun OverlayConfigWithDetails.toDomain(): OverlayConfig {
    val geometry = region?.let {
        NormalizedRect(it.xRatio, it.yRatio, it.widthRatio, it.heightRatio).sanitized()
    } ?: NormalizedRect.FullScreen
    return when (config.type) {
        OverlayType.BURN_MASK -> {
            val style = burnStyle ?: BurnMaskStyleEntity(
                config.id,
                0xFF000000,
                DEFAULT_BURN_MASK_ALPHA,
                12f,
            )
            BurnMaskConfig(
                id = config.id,
                name = config.name,
                enabled = config.enabled,
                rect = geometry,
                colorArgb = style.colorArgb,
                cornerRadiusDp = style.cornerRadiusDp,
                alpha = style.alpha.coerceIn(0f, 1f),
            )
        }

        OverlayType.TOUCH_BLOCK -> TouchBlockConfig(
            id = config.id,
            name = config.name,
            enabled = config.enabled,
            rect = geometry,
            boundaryVisible = touchStyle?.boundaryVisible ?: true,
            alpha = (touchStyle?.alpha ?: DEFAULT_TOUCH_BLOCK_ALPHA).coerceIn(0f, 1f),
        )

        OverlayType.WATERMARK -> {
            val style = watermarkStyle ?: WatermarkStyleEntity(
                configId = config.id,
                text = "屏幕工具箱",
                textSizeSp = 24f,
                fontWeight = 500,
                colorArgb = 0xFFFFFFFF,
                alpha = 0.35f,
                rotationDegrees = -20f,
                mode = WatermarkMode.TILED,
                tileSpacingDp = 96f,
                positionXRatio = 0.5f,
                positionYRatio = 0.5f,
            )
            WatermarkConfig(
                id = config.id,
                name = config.name,
                enabled = config.enabled,
                rect = NormalizedRect.FullScreen,
                text = style.text,
                textSizeSp = style.textSizeSp,
                fontWeight = style.fontWeight,
                colorArgb = style.colorArgb,
                alpha = style.alpha,
                rotationDegrees = style.rotationDegrees,
                mode = style.mode,
                tileSpacingDp = style.tileSpacingDp,
                positionXRatio = style.positionXRatio,
                positionYRatio = style.positionYRatio,
            )
        }
    }
}

internal fun AppRuleWithConfigIds.toDomain(): AppRule = AppRule(
    id = rule.id,
    packageName = rule.packageName,
    displayName = rule.displayName,
    enabled = rule.enabled,
    burnMaskEnabled = rule.burnMaskEnabled,
    touchBlockEnabled = rule.touchBlockEnabled,
    watermarkEnabled = rule.watermarkEnabled,
    configIds = configs.mapTo(mutableSetOf()) { it.id },
)

internal data class OverlayEntityParts(
    val config: OverlayConfigEntity,
    val region: OverlayRegionEntity,
    val burnStyle: BurnMaskStyleEntity? = null,
    val touchStyle: TouchBlockStyleEntity? = null,
    val watermarkStyle: WatermarkStyleEntity? = null,
)

internal fun OverlayConfig.toEntityParts(
    sortOrder: Int,
    existingCreatedAt: Long?,
    referenceWidthPx: Int,
    referenceHeightPx: Int,
    referenceOrientation: Int,
): OverlayEntityParts {
    val now = System.currentTimeMillis()
    val safeRect = rect.sanitized()
    val common = OverlayConfigEntity(
        id = id,
        name = name,
        type = type,
        enabled = enabled,
        sortOrder = sortOrder,
        createdAt = existingCreatedAt ?: now,
        updatedAt = now,
    )
    val region = OverlayRegionEntity(
        configId = id,
        xRatio = safeRect.x,
        yRatio = safeRect.y,
        widthRatio = safeRect.width,
        heightRatio = safeRect.height,
        referenceWidthPx = referenceWidthPx,
        referenceHeightPx = referenceHeightPx,
        referenceOrientation = referenceOrientation,
    )
    return when (this) {
        is BurnMaskConfig -> OverlayEntityParts(
            config = common,
            region = region,
            burnStyle = BurnMaskStyleEntity(
                id,
                colorArgb,
                alpha.coerceIn(0f, 1f),
                cornerRadiusDp.coerceAtLeast(0f),
            ),
        )

        is TouchBlockConfig -> OverlayEntityParts(
            config = common,
            region = region,
            touchStyle = TouchBlockStyleEntity(
                configId = id,
                boundaryVisible = boundaryVisible,
                alpha = alpha.coerceIn(0f, 1f),
            ),
        )

        is WatermarkConfig -> OverlayEntityParts(
            config = common.copy(type = OverlayType.WATERMARK),
            region = region.copy(
                xRatio = 0f,
                yRatio = 0f,
                widthRatio = 1f,
                heightRatio = 1f,
            ),
            watermarkStyle = WatermarkStyleEntity(
                configId = id,
                text = text.take(120),
                textSizeSp = textSizeSp.coerceIn(10f, 96f),
                fontWeight = fontWeight.coerceIn(100, 900),
                colorArgb = colorArgb,
                alpha = alpha.coerceIn(0f, 1f),
                rotationDegrees = rotationDegrees.coerceIn(-90f, 90f),
                mode = mode,
                tileSpacingDp = tileSpacingDp.coerceIn(32f, 240f),
                positionXRatio = positionXRatio.coerceIn(0f, 1f),
                positionYRatio = positionYRatio.coerceIn(0f, 1f),
            ),
        )
    }
}
