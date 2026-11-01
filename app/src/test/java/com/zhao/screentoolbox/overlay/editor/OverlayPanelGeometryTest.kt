package com.zhao.screentoolbox.overlay.editor

import com.zhao.screentoolbox.core.model.NormalizedRect
import com.zhao.screentoolbox.overlay.common.ScreenDrag
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPanelGeometryTest {
    @Test
    fun dragDeltaUsesRawScreenCoordinates() {
        val delta = ScreenDrag.between(
            previousRawX = 100.4f,
            previousRawY = 220.2f,
            rawX = 126.8f,
            rawY = 213.9f,
        )

        assertEquals(26, delta.first)
        assertEquals(-6, delta.second)
    }

    @Test
    fun resizedRectKeepsWindowInsideScreenWithMinimumSize() {
        val resized = OverlayPanelGeometry.resizeRect(
            rect = NormalizedRect(0.98f, 0.85f, 0.4f, 0.3f),
            widthPercent = 1,
            heightPercent = 60,
            screenWidth = 1_000,
            screenHeight = 2_000,
            minWidthPx = 48,
            minHeightPx = 96,
        )

        assertEquals(0.048f, resized.width, 0.0001f)
        assertEquals(0.6f, resized.height, 0.0001f)
        assertEquals(0.952f, resized.x, 0.0001f)
        assertEquals(0.4f, resized.y, 0.0001f)
    }

    @Test
    fun resizedRectCanReachOnePercentWithoutResizeHandlePadding() {
        val resized = OverlayPanelGeometry.resizeRect(
            rect = NormalizedRect(0.2f, 0.3f, 0.5f, 0.5f),
            widthPercent = 1,
            heightPercent = 1,
            screenWidth = 1_000,
            screenHeight = 2_000,
            minWidthPx = 4,
            minHeightPx = 4,
        )

        assertEquals(0.01f, resized.width, 0.0001f)
        assertEquals(0.01f, resized.height, 0.0001f)
    }

    @Test
    fun minimizedPanelSnapsToNearestHorizontalEdge() {
        assertEquals(
            PanelSnapSide.LEFT,
            OverlayPanelGeometry.nearestSide(
                x = 120,
                width = 56,
                screenWidth = 1_080,
            ),
        )
        assertEquals(
            PanelSnapSide.RIGHT,
            OverlayPanelGeometry.nearestSide(
                x = 760,
                width = 56,
                screenWidth = 1_080,
            ),
        )
    }

    @Test
    fun restoredPanelIsAnchoredAtIconSideAndKeptVisible() {
        val expandedX = OverlayPanelGeometry.expandedX(
            side = PanelSnapSide.RIGHT,
            panelWidth = 900,
            screenWidth = 1_080,
            marginPx = 24,
        )
        val expandedY = OverlayPanelGeometry.expandedY(
            iconCenterY = 1_850,
            panelHeight = 500,
            screenHeight = 2_000,
            marginPx = 24,
        )

        assertEquals(156, expandedX)
        assertEquals(1_476, expandedY)
    }
}
