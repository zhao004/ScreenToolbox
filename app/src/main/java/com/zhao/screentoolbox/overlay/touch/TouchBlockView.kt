package com.zhao.screentoolbox.overlay.touch

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.overlay.common.RegionEditView
import com.zhao.screentoolbox.overlay.common.overlayPrimaryColor

/** 有效防误触窗口保持透明，仅编辑态和可选边界提示可见。 */
class TouchBlockView(context: Context) : RegionEditView(context) {
    private val density = resources.displayMetrics.density
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private var showBoundary = true
    private var currentConfig: TouchBlockConfig? = null
    private var currentEditable = false

    fun bind(config: TouchBlockConfig, editable: Boolean) {
        currentConfig = config
        currentEditable = editable
        applyTheme()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme()
    }

    private fun applyTheme() {
        val config = currentConfig ?: return
        editable = currentEditable
        showBoundary = config.boundaryVisible
        val accent = context.overlayPrimaryColor()
        hintPaint.color = ColorUtils.setAlphaComponent(accent, 180)
        configurePaint(
            fillColor = ColorUtils.setAlphaComponent(accent, 28),
            borderColor = accent,
            cornerRadiusDp = 8f,
        )
    }

    override fun drawContent(canvas: Canvas, rect: RectF) {
        if (editable) {
            super.drawContent(canvas, rect)
        } else if (showBoundary) {
            val inset = 1f * density
            canvas.drawRoundRect(
                RectF(inset, inset, width - inset, height - inset),
                8f * density,
                8f * density,
                hintPaint,
            )
        }
    }
}
