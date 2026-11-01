package com.zhao.screentoolbox.overlay.touch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TouchBlockViewTest {
    @Test
    fun configuredAlphaControlsVisibleFillWithoutDisablingTouchBlocking() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var view: TouchBlockView
        lateinit var bitmap: Bitmap

        instrumentation.runOnMainSync {
            view = createView(context, alpha = 0.2f)
            bitmap = Bitmap.createBitmap(VIEW_SIZE_PX, VIEW_SIZE_PX, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }

        assertEquals(51, Color.alpha(bitmap.getPixel(VIEW_SIZE_PX / 2, VIEW_SIZE_PX / 2)))
        assertTrue(interceptsTouch(instrumentation, view))
    }

    @Test
    fun zeroAlphaKeepsFillTransparentWhileStillInterceptingTouch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var view: TouchBlockView
        lateinit var bitmap: Bitmap

        instrumentation.runOnMainSync {
            view = createView(context, alpha = 0f)
            bitmap = Bitmap.createBitmap(VIEW_SIZE_PX, VIEW_SIZE_PX, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }

        assertEquals(0, Color.alpha(bitmap.getPixel(VIEW_SIZE_PX / 2, VIEW_SIZE_PX / 2)))
        assertTrue(interceptsTouch(instrumentation, view))
    }

    private fun createView(context: Context, alpha: Float): TouchBlockView =
        TouchBlockView(context).apply {
            bind(
                config = TouchBlockConfig(
                    id = "touch-test",
                    name = "防误触测试",
                    enabled = true,
                    rect = NormalizedRect.FullScreen,
                    boundaryVisible = false,
                    alpha = alpha,
                ),
                editable = false,
            )
            measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, VIEW_SIZE_PX, VIEW_SIZE_PX)
        }

    private fun interceptsTouch(
        instrumentation: android.app.Instrumentation,
        view: TouchBlockView,
    ): Boolean {
        var intercepted = false
        instrumentation.runOnMainSync {
            val event = MotionEvent.obtain(
                0L,
                0L,
                MotionEvent.ACTION_DOWN,
                VIEW_SIZE_PX / 2f,
                VIEW_SIZE_PX / 2f,
                0,
            )
            try {
                intercepted = view.onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        return intercepted
    }

    private companion object {
        const val VIEW_SIZE_PX = 40
    }
}
