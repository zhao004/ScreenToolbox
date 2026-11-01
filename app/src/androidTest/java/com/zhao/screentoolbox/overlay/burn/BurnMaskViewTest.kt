package com.zhao.screentoolbox.overlay.burn

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhao.screentoolbox.core.model.BurnMaskConfig
import com.zhao.screentoolbox.core.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BurnMaskViewTest {
    @Test
    fun configuredAlphaControlsRenderedMaskOpacity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bitmap: Bitmap

        instrumentation.runOnMainSync {
            val view = BurnMaskView(context).apply {
                bind(
                    config = BurnMaskConfig(
                        id = "opaque-test",
                        name = "不透明测试",
                        enabled = true,
                        rect = NormalizedRect.FullScreen,
                        colorArgb = 0x00000000,
                        alpha = 0.6f,
                    ),
                    editable = false,
                )
                measure(
                    View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY),
                )
                layout(0, 0, 20, 20)
            }
            bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }

        assertEquals(0x99000000.toInt(), bitmap.getPixel(10, 10))
    }
}
