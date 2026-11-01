package com.zhao.screentoolbox.overlay.burn

import org.junit.Assert.assertEquals
import org.junit.Test

class BurnMaskColorTest {
    @Test
    fun opaqueBurnMaskColor_ignoresSourceAlphaAndKeepsRgb() {
        val color = opaqueBurnMaskColor(0x00123456)

        assertEquals(0xFF123456.toInt(), color)
    }

    @Test
    fun opaqueBurnMaskColor_keepsBlackFullyOpaque() {
        val color = opaqueBurnMaskColor(0x00000000)

        assertEquals(0xFF000000.toInt(), color)
    }
}
