package com.zhao.screentoolbox.overlay.burn

import org.junit.Assert.assertEquals
import org.junit.Test

class BurnMaskColorTest {
    @Test
    fun burnMaskFillColor_ignoresSourceAlphaAndAppliesConfiguredAlpha() {
        val color = burnMaskFillColor(colorArgb = 0x00123456, alpha = 0.6f)

        assertEquals(0x99123456.toInt(), color)
    }

    @Test
    fun burnMaskFillColor_keepsBlackFullyOpaqueWhenConfiguredOpaque() {
        val color = burnMaskFillColor(colorArgb = 0x00000000, alpha = 1f)

        assertEquals(0xFF000000.toInt(), color)
    }

    @Test
    fun burnMaskFillColor_clampsAlphaToTransparentBounds() {
        val transparentWhite = burnMaskFillColor(colorArgb = 0xFFFFFFFF, alpha = -1f)
        assertEquals(0x00FFFFFF, transparentWhite)
        assertEquals(
            0xFF000000.toInt(),
            burnMaskFillColor(colorArgb = 0xFF000000, alpha = 2f),
        )
    }
}
