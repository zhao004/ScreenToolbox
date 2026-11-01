package com.zhao.screentoolbox.data.repository

import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.data.database.BurnMaskStyleEntity
import com.zhao.screentoolbox.data.database.OverlayConfigWithDetails
import com.zhao.screentoolbox.data.database.TouchBlockStyleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryMappersTest {
    @Test
    fun burnMaskTransparencyRoundTrips() {
        val config = BurnMaskConfig(
            id = "burn",
            name = "遮罩",
            enabled = true,
            rect = NormalizedRect.FullScreen,
            colorArgb = 0x80123456,
            alpha = 0.6f,
        )

        val parts = config.toEntityParts(
            sortOrder = 0,
            existingCreatedAt = null,
            referenceWidthPx = 1080,
            referenceHeightPx = 2400,
            referenceOrientation = 0,
        )
        assertEquals(0.6f, parts.burnStyle?.alpha)

        val style = parts.burnStyle
            ?: error("防烧屏映射缺少样式")
        val restored = OverlayConfigWithDetails(
            config = parts.config,
            region = parts.region,
            burnStyle = BurnMaskStyleEntity(
                configId = style.configId,
                colorArgb = style.colorArgb,
                alpha = style.alpha,
                cornerRadiusDp = style.cornerRadiusDp,
            ),
            touchStyle = null,
            watermarkStyle = null,
        ).toDomain()

        assertTrue(restored is BurnMaskConfig)
        assertEquals(0x80123456, (restored as BurnMaskConfig).colorArgb)
        assertEquals(0.6f, restored.alpha)
    }

    @Test
    fun touchBlockTransparencyRoundTripsAndClampsOutOfRangeValues() {
        val config = TouchBlockConfig(
            id = "touch",
            name = "防误触",
            enabled = true,
            rect = NormalizedRect(0f, 0.8f, 1f, 0.2f),
            boundaryVisible = false,
            alpha = 2f,
        )

        val parts = config.toEntityParts(
            sortOrder = 0,
            existingCreatedAt = null,
            referenceWidthPx = 1080,
            referenceHeightPx = 2400,
            referenceOrientation = 0,
        )
        val style = parts.touchStyle ?: error("防误触映射缺少样式")
        assertEquals(1f, style.alpha)

        val restored = OverlayConfigWithDetails(
            config = parts.config,
            region = parts.region,
            burnStyle = null,
            touchStyle = TouchBlockStyleEntity(
                configId = style.configId,
                boundaryVisible = style.boundaryVisible,
                alpha = -1f,
            ),
            watermarkStyle = null,
        ).toDomain()

        assertTrue(restored is TouchBlockConfig)
        assertEquals(0f, (restored as TouchBlockConfig).alpha)
    }
}
