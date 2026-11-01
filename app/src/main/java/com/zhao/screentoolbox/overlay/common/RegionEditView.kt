package com.zhao.screentoolbox.overlay.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * 防烧屏和防误触共用的区域绘制与手势层。
 *
 * 展示态根据具体子类决定是否绘制提示；编辑态只绘制边框，区域尺寸由悬浮面板控制。
 */
abstract class RegionEditView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val bounds = RectF()

    var editable: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var onMove: ((dx: Int, dy: Int) -> Unit)? = null
    var onGestureStart: (() -> Unit)? = null
    var onCommit: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private var moving = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dispatchedRawX = 0f
    private var dispatchedRawY = 0f
    private var frameUpdatePosted = false
    private val frameUpdate = Runnable {
        frameUpdatePosted = false
        dispatchPendingDelta()
    }

    protected fun configurePaint(fillColor: Int, borderColor: Int, cornerRadiusDp: Float) {
        fillPaint.color = fillColor
        borderPaint.color = borderColor
        cornerRadiusPx = cornerRadiusDp * density
        invalidate()
    }

    private var cornerRadiusPx = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        drawContent(canvas, bounds)
        if (editable) {
            canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, borderPaint)
        }
    }

    protected open fun drawContent(canvas: Canvas, rect: RectF) {
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, fillPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editable) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointerIndex = event.actionIndex
                activePointerId = event.getPointerId(pointerIndex)
                moving = true
                lastRawX = event.rawX
                lastRawY = event.rawY
                dispatchedRawX = lastRawX
                dispatchedRawY = lastRawY
                onGestureStart?.invoke()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex >= 0) {
                    lastRawX = event.getX(pointerIndex) + windowLeftOnScreen(event)
                    lastRawY = event.getY(pointerIndex) + windowTopOnScreen(event)
                    scheduleFrameUpdate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex >= 0) {
                    lastRawX = event.getX(pointerIndex) + windowLeftOnScreen(event)
                    lastRawY = event.getY(pointerIndex) + windowTopOnScreen(event)
                }
                removeCallbacks(frameUpdate)
                frameUpdatePosted = false
                dispatchPendingDelta()
                finishGesture(commit = true)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelFrameUpdate()
                finishGesture(commit = false)
                return true
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        cancelFrameUpdate()
        super.onDetachedFromWindow()
    }

    private fun scheduleFrameUpdate() {
        if (frameUpdatePosted) return
        frameUpdatePosted = true
        postOnAnimation(frameUpdate)
    }

    private fun dispatchPendingDelta() {
        val delta = ScreenDrag.between(
            previousRawX = dispatchedRawX,
            previousRawY = dispatchedRawY,
            rawX = lastRawX,
            rawY = lastRawY,
        )
        if (delta.first == 0 && delta.second == 0) return
        dispatchedRawX = lastRawX
        dispatchedRawY = lastRawY
        if (moving) {
            onMove?.invoke(delta.first, delta.second)
        }
    }

    private fun finishGesture(commit: Boolean) {
        if (commit) {
            onCommit?.invoke()
        } else {
            onCancel?.invoke()
        }
        activePointerId = MotionEvent.INVALID_POINTER_ID
        moving = false
    }

    private fun cancelFrameUpdate() {
        removeCallbacks(frameUpdate)
        frameUpdatePosted = false
    }

    private fun windowLeftOnScreen(event: MotionEvent): Float = event.rawX - event.x

    private fun windowTopOnScreen(event: MotionEvent): Float = event.rawY - event.y
}
