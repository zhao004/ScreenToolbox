package com.zhao.screentoolbox.overlay.touch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.zhao.screentoolbox.core.model.TouchBlockConfig
import com.zhao.screentoolbox.overlay.common.RegionEditView

/** 有效防误触窗口保持透明，仅编辑态和可选边界提示可见。 */
class TouchBlockView(context: Context) : RegionEditView(context) {
    private val density = resources.displayMetrics.density
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = Color.argb(180, 37, 99, 235)
    }
    private var showBoundary = true

    fun bind(config: TouchBlockConfig, editable: Boolean) {
        this.editable = editable
        showBoundary = config.boundaryVisible
        configurePaint(
            fillColor = Color.argb(28, 37, 99, 235),
            borderColor = Color.rgb(37, 99, 235),
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
