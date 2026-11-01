package com.zhao.screentoolbox.overlay.editor

import android.app.Instrumentation
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.OverlayConfig
import com.zhao.screentoolbox.core.model.TouchBlockConfig
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
                showOpacityControl = false,
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
        assertFalse(
            findViews(panel) { it is TextView }
                .filterIsInstance<TextView>()
                .any { it.text == "透明度" },
        )
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

    @Test
    fun newBurnMaskShowsDraggableOpacitySliderWithSafeEndInsets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var emittedConfig: OverlayConfig? = null
        val panel = bindPanel(
            context = context,
            instrumentation = instrumentation,
            config = BurnMaskConfig(
                id = "new-burn",
                name = "新建防烧屏",
                enabled = true,
                rect = NormalizedRect(0.1f, 0.2f, 0.5f, 0.1f),
                alpha = 0.6f,
            ),
            showOpacityControl = true,
            onConfigChanged = { emittedConfig = it },
        )

        assertTrue(
            findViews(panel) { it is TextView }
                .filterIsInstance<TextView>()
                .any { it.text == "60%" },
        )
        val seekBars = findViews(panel) { it is SeekBar }.filterIsInstance<SeekBar>()
        assertTrue(seekBars.isNotEmpty())
        assertTrue(
            seekBars.all { seekBar ->
                val minimumInset = (seekBar.thumb?.intrinsicWidth ?: 0) / 2
                seekBar.paddingLeft >= minimumInset && seekBar.paddingRight >= minimumInset
            },
        )

        val opacitySlider = seekBars.single { it.contentDescription == "透明度" }
        dragSliderTo(instrumentation, opacitySlider, fraction = 0.8f)

        assertEquals(0.8f, (emittedConfig as BurnMaskConfig).alpha, 0.03f)
    }

    @Test
    fun newTouchBlockShowsOpacitySliderWithTwentyPercentDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val panel = bindPanel(
            context = context,
            instrumentation = instrumentation,
            config = TouchBlockConfig(
                id = "new-touch",
                name = "新建防误触",
                enabled = true,
                rect = NormalizedRect(0f, 0.8f, 1f, 0.2f),
                alpha = 0.2f,
            ),
            showOpacityControl = true,
            onConfigChanged = {},
        )

        assertTrue(
            findViews(panel) { it is SeekBar }
                .filterIsInstance<SeekBar>()
                .any { it.contentDescription == "透明度" },
        )
        assertTrue(
            findViews(panel) { it is TextView }
                .filterIsInstance<TextView>()
                .any { it.text == "20%" },
        )
    }

    private fun bindPanel(
        context: Context,
        instrumentation: Instrumentation,
        config: OverlayConfig,
        showOpacityControl: Boolean,
        onConfigChanged: (OverlayConfig) -> Unit,
    ): OverlayEditorPanelView {
        lateinit var panel: OverlayEditorPanelView
        instrumentation.runOnMainSync {
            panel = OverlayEditorPanelView(context).apply {
                bind(
                    config = config,
                    showOpacityControl = showOpacityControl,
                    screenWidth = 1_000,
                    screenHeight = 2_000,
                    minWidthPx = 4,
                    minHeightPx = 4,
                    onConfigChanged = onConfigChanged,
                    onSave = {},
                    onCancel = {},
                )
                measure(
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1_400, View.MeasureSpec.EXACTLY),
                )
                layout(0, 0, 800, 1_400)
            }
        }
        return panel
    }

    private fun dragSliderTo(
        instrumentation: Instrumentation,
        seekBar: SeekBar,
        fraction: Float,
    ) {
        instrumentation.runOnMainSync {
            val trackStart = seekBar.paddingLeft.toFloat()
            val trackEnd = (seekBar.width - seekBar.paddingRight).toFloat()
            val x = trackStart + (trackEnd - trackStart) * fraction
            val y = seekBar.height / 2f
            val downTime = android.os.SystemClock.uptimeMillis()
            val events = listOf(
                MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0),
                MotionEvent.obtain(downTime, downTime + 16, MotionEvent.ACTION_MOVE, x, y, 0),
                MotionEvent.obtain(downTime, downTime + 32, MotionEvent.ACTION_UP, x, y, 0),
            )
            try {
                events.forEach(seekBar::dispatchTouchEvent)
            } finally {
                events.forEach { it.recycle() }
            }
        }
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
