package com.zhao.screentoolbox.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedRectTest {
    @Test
    fun sanitized_clampsPositionAndMinimumSize() {
        val rect = NormalizedRect(2f, -1f, 0f, 2f).sanitized()

        assertTrue(rect.x in 0f..1f)
        assertTrue(rect.y in 0f..1f)
        assertTrue(rect.width > 0f)
        assertEquals(1f, rect.height, 0.0001f)
    }

    @Test
    fun clamped_respectsPixelMinimumAndScreenBounds() {
        val rect = NormalizedRect(0.99f, 0.99f, 0.01f, 0.01f)
            .clamped(1000, 2000, minWidthPx = 100, minHeightPx = 200)

        assertEquals(0.1f, rect.width, 0.0001f)
        assertEquals(0.1f, rect.height, 0.0001f)
        assertEquals(0.9f, rect.x, 0.0001f)
        assertEquals(0.9f, rect.y, 0.0001f)
    }

    @Test
    fun pixelsRoundTripThroughNormalizedCoordinates() {
        val rect = normalizedRectFromPixels(
            x = 100,
            y = 200,
            width = 300,
            height = 400,
            screenWidth = 1000,
            screenHeight = 2000,
            minWidthPx = 48,
            minHeightPx = 48,
        )

        assertEquals(0.1f, rect.x, 0.0001f)
        assertEquals(0.1f, rect.y, 0.0001f)
        assertEquals(0.3f, rect.width, 0.0001f)
        assertEquals(0.2f, rect.height, 0.0001f)
    }
}
