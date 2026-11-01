package com.zhao.screentoolbox.overlay.watermark

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.zhao.screentoolbox.core.model.WatermarkConfig
import com.zhao.screentoolbox.core.model.WatermarkMode
import com.zhao.screentoolbox.overlay.common.overlayPrimaryColor
import kotlin.math.max

/** 水印使用 Canvas 绘制，避免把系统窗口与 Compose 生命周期耦合。 */
class WatermarkView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }
    private val dragIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var config: WatermarkConfig? = null
    private var editable = false
    private var dragX = 0f
    private var dragY = 0f

    var onPositionChanged: ((xRatio: Float, yRatio: Float) -> Unit)? = null
    var onCommit: (() -> Unit)? = null

    fun bind(value: WatermarkConfig, isEditable: Boolean) {
        config = value
        editable = isEditable
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value.textSizeSp,
            resources.displayMetrics,
        )
        textPaint.color = Color.argb(
            255,
            Color.red(value.colorArgb.toInt()),
            Color.green(value.colorArgb.toInt()),
            Color.blue(value.colorArgb.toInt()),
        )
        textPaint.typeface = Typeface.create("sans-serif", weightToStyle(value.fontWeight))
        dragX = value.positionXRatio * width
        dragY = value.positionYRatio * height
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        config?.let {
            dragX = it.positionXRatio * w
            dragY = it.positionYRatio * h
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = config ?: return
        if (current.text.isBlank()) return
        textPaint.alpha = 255
        if (editable && current.mode == WatermarkMode.SINGLE) {
            dragIndicatorPaint.color = ColorUtils.setAlphaComponent(
                context.overlayPrimaryColor(),
                120,
            )
            canvas.drawCircle(dragX, dragY, 8f * density, dragIndicatorPaint)
        }
        canvas.save()
        when (current.mode) {
            WatermarkMode.SINGLE -> {
                canvas.rotate(current.rotationDegrees, dragX, dragY)
                canvas.drawText(current.text, dragX, dragY, textPaint)
            }

            WatermarkMode.TILED -> {
                canvas.rotate(current.rotationDegrees, width / 2f, height / 2f)
                val textWidth = textPaint.measureText(current.text)
                val stepX = max(1f, textWidth + current.tileSpacingDp * density)
                val stepY = max(1f, current.textSizeSp * density + current.tileSpacingDp * 0.55f * density)
                var y = -height.toFloat()
                while (y < height * 2f) {
                    var x = -width.toFloat()
                    while (x < width * 2f) {
                        canvas.drawText(current.text, x, y, textPaint)
                        x += stepX
                    }
                    y += stepY
                }
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val current = config
        if (!editable || current?.mode != WatermarkMode.SINGLE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragX = event.x
                dragY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                dragX = event.x.coerceIn(0f, width.toFloat())
                dragY = event.y.coerceIn(0f, height.toFloat())
                onPositionChanged?.invoke(
                    if (width > 0) dragX / width else 0.5f,
                    if (height > 0) dragY / height else 0.5f,
                )
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onCommit?.invoke()
                return true
            }
        }
        return true
    }

    private fun weightToStyle(weight: Int): Int = when {
        weight >= 700 -> Typeface.BOLD
        weight >= 600 -> Typeface.BOLD
        else -> Typeface.NORMAL
    }
}
