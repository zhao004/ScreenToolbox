package com.zhao.screentoolbox.data.repository

import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.data.database.BurnMaskStyleEntity
import com.zhao.screentoolbox.data.database.OverlayConfigWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryMappersTest {
    @Test
    fun burnMaskPersistsOpaqueAlphaAndIgnoresLegacyValueOnRead() {
        val config = BurnMaskConfig(
            id = "burn",
            name = "遮罩",
            enabled = true,
            rect = NormalizedRect.FullScreen,
            colorArgb = 0x80123456,
        )

        val parts = config.toEntityParts(
            sortOrder = 0,
            existingCreatedAt = null,
            referenceWidthPx = 1080,
            referenceHeightPx = 2400,
            referenceOrientation = 0,
        )
        assertEquals(1f, parts.burnStyle?.alpha)

        val legacyStyle = parts.burnStyle?.copy(alpha = 0.2f)
            ?: error("防烧屏映射缺少样式")
        val restored = OverlayConfigWithDetails(
            config = parts.config,
            region = parts.region,
            burnStyle = BurnMaskStyleEntity(
                configId = legacyStyle.configId,
                colorArgb = legacyStyle.colorArgb,
                alpha = legacyStyle.alpha,
                cornerRadiusDp = legacyStyle.cornerRadiusDp,
            ),
            touchStyle = null,
            watermarkStyle = null,
        ).toDomain()

        assertTrue(restored is BurnMaskConfig)
        assertEquals(0x80123456, (restored as BurnMaskConfig).colorArgb)
    }
}
