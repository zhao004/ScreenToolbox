package com.zhao.screentoolbox.overlay

import android.view.WindowManager
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.core.model.WatermarkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowPolicyTest {
    @Test
    fun burnMaskPassesThroughTouchesAndDrawsBehindSystemBars() {
        val flags = OverlayWindowPolicy.flags(
            config = BurnMaskConfig(
                id = "burn",
                name = "遮罩",
                enabled = true,
                rect = NormalizedRect.FullScreen,
            ),
            editable = false,
        )

        assertTrue(hasFlag(flags, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN))
        assertTrue(hasFlag(flags, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS))
        assertTrue(hasFlag(flags, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
    }

    @Test
    fun touchBlockRemainsTouchable() {
        val flags = OverlayWindowPolicy.flags(
            config = TouchBlockConfig(
                id = "touch",
                name = "防误触",
                enabled = true,
                rect = NormalizedRect.FullScreen,
            ),
            editable = false,
        )

        assertTrue(!hasFlag(flags, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
    }

    @Test
    fun watermarkRemainsPassThroughAndDoesNotClaimSystemBarBackground() {
        val flags = OverlayWindowPolicy.flags(
            config = WatermarkConfig(
                id = "watermark",
                name = "水印",
                enabled = true,
            ),
            editable = false,
        )

        assertTrue(hasFlag(flags, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
        assertEquals(
            0,
            flags and WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
        )
    }

    private fun hasFlag(flags: Int, flag: Int): Boolean = flags and flag != 0
}
