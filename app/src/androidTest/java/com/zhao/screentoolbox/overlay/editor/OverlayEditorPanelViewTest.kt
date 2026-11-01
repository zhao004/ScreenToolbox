package com.zhao.screentoolbox.overlay.editor

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.OverlayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayEditorPanelViewTest {
    @Test
    fun selectingColorMovesSelectionFocusInsideScrollablePalette() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var emittedConfig: OverlayConfig? = null
        lateinit var panel: OverlayEditorPanelView

        instrumentation.runOnMainSync {
            panel = OverlayEditorPanelView(context)
            panel.bind(
                config = BurnMaskConfig(
                    id = "color-test",
                    name = "颜色测试",
                    enabled = true,
                    rect = NormalizedRect(0.1f, 0.2f, 0.5f, 0.1f),
                ),
                screenWidth = 1_000,
                screenHeight = 2_000,
                minWidthPx = 4,
                minHeightPx = 4,
                onConfigChanged = { emittedConfig = it },
                onSave = {},
                onCancel = {},
            )
        }

        val swatches = findViews(panel) { view ->
            view.tag?.toString()?.startsWith("color-swatch:") == true
        }
        val header = panel.getChildAt(0) as ViewGroup
        val nameInput = findViews(panel) { it is EditText }.single() as EditText
        assertEquals("拖动编辑面板", header.getChildAt(0).contentDescription)
        assertTrue(swatches.size >= 8)
        assertNotNull(findViews(panel) { it is HorizontalScrollView }.firstOrNull())
        val black = swatches.first { it.tag.toString() == "color-swatch:FF000000" }
        val red = swatches.first { it.tag.toString() == "color-swatch:FFEF4444" }
        assertTrue(black.isSelected)
        assertFalse(red.isSelected)

        instrumentation.runOnMainSync { nameInput.setText("自定义防烧屏") }
        instrumentation.runOnMainSync { red.performClick() }

        assertEquals("自定义防烧屏", (emittedConfig as BurnMaskConfig).name)
        assertTrue(red.isSelected)
        assertFalse(black.isSelected)
        assertEquals(0xFFEF4444, (emittedConfig as BurnMaskConfig).colorArgb)
    }

    private fun findViews(root: View, predicate: (View) -> Boolean): List<View> {
        val matches = mutableListOf<View>()
        if (predicate(root)) matches += root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                matches += findViews(root.getChildAt(index), predicate)
            }
        }
        return matches
    }
}
