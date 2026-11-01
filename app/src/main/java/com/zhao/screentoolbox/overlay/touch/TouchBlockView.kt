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
import kotlin.math.roundToInt

private const val EDITING_FILL_ALPHA = 28
private const val BOUNDARY_ALPHA = 180
private const val MAX_ALPHA = 255

/** 防误触窗口按配置透明度显示填充，触摸拦截不依赖其可见程度。 */
class TouchBlockView(context: Context) : RegionEditView(context) {
    private val density = resources.displayMetrics.density
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private var showBoundary = true
    private var fillVisible = false
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
        val configuredAlpha = (config.alpha.coerceIn(0f, 1f) * MAX_ALPHA).roundToInt()
        val fillAlpha = if (editable) {
            maxOf(configuredAlpha, EDITING_FILL_ALPHA)
        } else {
            configuredAlpha
        }
        fillVisible = fillAlpha > 0
        hintPaint.color = ColorUtils.setAlphaComponent(accent, BOUNDARY_ALPHA)
        configurePaint(
            fillColor = ColorUtils.setAlphaComponent(accent, fillAlpha),
            borderColor = accent,
            cornerRadiusDp = 8f,
        )
    }

    override fun drawContent(canvas: Canvas, rect: RectF) {
        if (editable || fillVisible) {
            super.drawContent(canvas, rect)
        }
        if (!editable && showBoundary) {
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
