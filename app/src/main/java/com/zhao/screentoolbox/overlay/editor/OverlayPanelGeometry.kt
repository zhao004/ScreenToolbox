package com.zhao.screentoolbox.overlay.editor

import com.zhao.screentoolbox.core.model.NormalizedRect
import kotlin.math.max
import kotlin.math.min

/** 悬浮编辑图标吸附的屏幕侧边。 */
internal enum class PanelSnapSide {
    LEFT,
    RIGHT,
}

/** 悬浮编辑面板与区域尺寸共用的纯几何计算。 */
internal object OverlayPanelGeometry {
    /** 按百分比调整区域尺寸，并将越界区域平移回屏幕内。 */
    fun resizeRect(
        rect: NormalizedRect,
        widthPercent: Int,
        heightPercent: Int,
        screenWidth: Int,
        screenHeight: Int,
        minWidthPx: Int,
        minHeightPx: Int,
    ): NormalizedRect {
        if (screenWidth <= 0 || screenHeight <= 0) return rect.sanitized()
        val minWidth = min(1f, minWidthPx.toFloat() / screenWidth)
        val minHeight = min(1f, minHeightPx.toFloat() / screenHeight)
        val width = (widthPercent.coerceIn(0, 100) / 100f).coerceAtLeast(minWidth)
        val height = (heightPercent.coerceIn(0, 100) / 100f).coerceAtLeast(minHeight)
        return NormalizedRect(
            x = rect.x,
            y = rect.y,
            width = width,
            height = height,
        ).sanitized()
    }

    /** 根据图标中心点选择最近的左右屏幕边缘。 */
    fun nearestSide(x: Int, width: Int, screenWidth: Int): PanelSnapSide {
        val centerX = x.toFloat() + width / 2f
        return if (centerX <= screenWidth / 2f) {
            PanelSnapSide.LEFT
        } else {
            PanelSnapSide.RIGHT
        }
    }

    /** 返回图标吸附后的横向位置。 */
    fun snappedX(side: PanelSnapSide, width: Int, screenWidth: Int): Int {
        val maxX = max(0, screenWidth - width)
        return when (side) {
            PanelSnapSide.LEFT -> 0
            PanelSnapSide.RIGHT -> maxX
        }
    }

    /** 在屏幕范围内夹取浮动面板坐标。 */
    fun clampedOffset(value: Int, size: Int, screenSize: Int): Int {
        return value.coerceIn(0, max(0, screenSize - size))
    }

    /** 从图标所在侧展开完整面板。 */
    fun expandedX(
        side: PanelSnapSide,
        panelWidth: Int,
        screenWidth: Int,
        marginPx: Int,
    ): Int {
        val maxX = max(0, screenWidth - panelWidth)
        val margin = marginPx.coerceIn(0, maxX / 2)
        return when (side) {
            PanelSnapSide.LEFT -> margin
            PanelSnapSide.RIGHT -> maxX - margin
        }
    }

    /** 以图标纵向中心展开完整面板，并保证面板上下边缘可见。 */
    fun expandedY(
        iconCenterY: Int,
        panelHeight: Int,
        screenHeight: Int,
        marginPx: Int,
    ): Int {
        val maxY = max(0, screenHeight - panelHeight)
        val margin = marginPx.coerceIn(0, maxY / 2)
        return (iconCenterY - panelHeight / 2).coerceIn(margin, maxY - margin)
    }
}
