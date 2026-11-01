package com.zhao.screentoolbox.overlay.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

enum class ResizeCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

/**
 * 防烧屏和防误触共用的区域绘制与手势层。
 *
 * 展示态根据具体子类决定是否绘制提示；编辑态统一绘制边框和四个缩放手柄。
 */
abstract class RegionEditView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.rgb(37, 99, 235)
    }
    private val bounds = RectF()

    var editable: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var onMove: ((dx: Int, dy: Int) -> Unit)? = null
    var onResize: ((corner: ResizeCorner, dx: Int, dy: Int) -> Unit)? = null
    var onCommit: (() -> Unit)? = null

    private var activeCorner: ResizeCorner? = null
    private var moving = false
    private var lastX = 0f
    private var lastY = 0f

    protected fun configurePaint(fillColor: Int, borderColor: Int, cornerRadiusDp: Float) {
        fillPaint.color = fillColor
        borderPaint.color = borderColor
        cornerRadiusPx = cornerRadiusDp * density
        invalidate()
    }

    private var cornerRadiusPx = 0f
    private val handleRadius = 11f * density
    private val handleHitRadius = 28f * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        drawContent(canvas, bounds)
        if (editable) {
            canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, borderPaint)
            drawHandles(canvas)
        }
    }

    protected open fun drawContent(canvas: Canvas, rect: RectF) {
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, fillPaint)
    }

    private fun drawHandles(canvas: Canvas) {
        val inset = handleRadius
        listOf(
            inset to inset,
            width - inset to inset,
            inset to height - inset,
            width - inset to height - inset,
        ).forEach { (x, y) ->
            canvas.drawCircle(x, y, handleRadius, handlePaint)
            canvas.drawCircle(x, y, handleRadius, handleBorderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editable) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeCorner = findCorner(event.x, event.y)
                moving = activeCorner == null
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - lastX).toInt()
                val dy = (event.y - lastY).toInt()
                if (dx != 0 || dy != 0) {
                    lastX = event.x
                    lastY = event.y
                    val corner = activeCorner
                    if (corner != null) {
                        onResize?.invoke(corner, dx, dy)
                    } else if (moving) {
                        onMove?.invoke(dx, dy)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onCommit?.invoke()
                activeCorner = null
                moving = false
                return true
            }
        }
        return true
    }

    private fun findCorner(x: Float, y: Float): ResizeCorner? {
        val corners = mapOf(
            ResizeCorner.TOP_LEFT to (0f to 0f),
            ResizeCorner.TOP_RIGHT to (width.toFloat() to 0f),
            ResizeCorner.BOTTOM_LEFT to (0f to height.toFloat()),
            ResizeCorner.BOTTOM_RIGHT to (width.toFloat() to height.toFloat()),
        )
        return corners.entries.firstOrNull { (_, point) ->
            abs(x - point.first) <= handleHitRadius && abs(y - point.second) <= handleHitRadius
        }?.key
    }
}
