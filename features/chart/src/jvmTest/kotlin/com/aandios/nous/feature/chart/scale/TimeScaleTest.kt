/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.scale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeScaleTest {

    @Test
    fun `panBy clamps to scroll bounds`() {
        val scale = TimeScale()
        val maxScroll = scale.maxScroll(candleCount = 100, chartWidth = 800f)

        scale.panBy(deltaX = -100f, candleCount = 100, chartWidth = 800f)
        assertEquals(100f, scale.scrollOffset, 0.01f)

        scale.panBy(deltaX = -1_000_000f, candleCount = 100, chartWidth = 800f)
        assertEquals(maxScroll, scale.scrollOffset, 0.01f)

        scale.panBy(deltaX = 1_000_000f, candleCount = 100, chartWidth = 800f)
        assertEquals(-TimeScale.MAX_SCROLL_LEFT, scale.scrollOffset, 0.01f)
    }

    @Test
    fun `zoomAt without ctrl keeps right edge at latest candle`() {
        val scale = TimeScale()
        scale.scrollToLatest(candleCount = 500, chartWidth = 800f)

        scale.zoomAt(
            factor = 1.25f,
            mouseX = 0f,
            anchorAtMouse = false,
            chartWidth = 800f,
            candleCount = 500,
            minZoom = 0.05f,
            maxZoom = 4f,
        )

        assertEquals(1.25f, scale.zoomLevel)
        assertEquals(scale.maxScroll(500, 800f), scale.scrollOffset, 0.01f)
    }

    @Test
    fun `zoomAt with ctrl keeps candle under cursor fixed`() {
        val scale = TimeScale()
        scale.panBy(deltaX = -50f, candleCount = 1000, chartWidth = 800f)
        val mouseX = 200f
        val virtualBefore = mouseX + scale.scrollOffset

        scale.zoomAt(
            factor = 1.25f,
            mouseX = mouseX,
            anchorAtMouse = true,
            chartWidth = 800f,
            candleCount = 1000,
            minZoom = 0.05f,
            maxZoom = 4f,
        )

        assertEquals(virtualBefore * 1.25f, mouseX + scale.scrollOffset, 0.001f)
    }

    @Test
    fun `zoomAt respects min and max zoom bounds`() {
        val scale = TimeScale()

        scale.zoomAt(1000f, 0f, false, 800f, 100, minZoom = 0.05f, maxZoom = 4f)
        assertEquals(4f, scale.zoomLevel)

        scale.zoomAt(0.0001f, 0f, false, 800f, 100, minZoom = 0.05f, maxZoom = 4f)
        assertEquals(0.05f, scale.zoomLevel)
    }

    @Test
    fun `indexToX and xToIndex are consistent`() {
        val scale = TimeScale()
        scale.panBy(deltaX = -50f, candleCount = 1000, chartWidth = 800f)

        for (index in listOf(0, 5, 42)) {
            val x = scale.indexToX(index)
            assertEquals(index, scale.xToIndex(x))
        }
    }

    @Test
    fun `offsetAfterPrepend shifts scroll right by prepended width`() {
        val scale = TimeScale()
        scale.panBy(deltaX = -100f, candleCount = 10_000, chartWidth = 800f)
        val before = scale.scrollOffset

        scale.offsetAfterPrepend(addedCount = 200, candleCount = 10_000, chartWidth = 800f)

        assertEquals(before + 200 * scale.totalWidth(), scale.scrollOffset, 0.01f)
    }

    @Test
    fun `scrollToLatest moves to newest candle`() {
        val scale = TimeScale()
        scale.panBy(deltaX = 10_000f, candleCount = 1000, chartWidth = 800f)

        scale.scrollToLatest(candleCount = 1000, chartWidth = 800f)

        assertEquals(scale.maxScroll(1000, 800f), scale.scrollOffset, 0.01f)
        assertTrue(scale.isAtLatest(1000, 800f))
    }

    @Test
    fun `isAtLatest is false when scrolled away`() {
        val scale = TimeScale()
        scale.scrollToLatest(candleCount = 1000, chartWidth = 800f)
        scale.panBy(deltaX = 100f, candleCount = 1000, chartWidth = 800f)

        assertFalse(scale.isAtLatest(1000, 800f))
    }
}
