/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering

import com.aandios.nous.api.market.model.FootprintLevel
import com.aandios.nous.feature.chart.ui.FootprintRingDiameter
import com.aandios.nous.feature.chart.ui.FootprintRingWidth
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FootprintRendererTest {

    @Test
    fun `aggregateLevels returns input for BaseTick`() {
        val levels = listOf(
            level("100.0", bid = "1", ask = "2"),
            level("100.1", bid = "3", ask = "4"),
        )

        assertEquals(levels, aggregateLevels(levels, AggregationLevel.BaseTick, tickSize = 1.0))
    }

    @Test
    fun `aggregateLevels returns input for non positive tick size`() {
        val levels = listOf(level("100.0", bid = "1", ask = "2"))

        assertEquals(levels, aggregateLevels(levels, AggregationLevel.TenTick, tickSize = 0.0))
        assertEquals(levels, aggregateLevels(levels, AggregationLevel.TenTick, tickSize = -1.0))
    }

    @Test
    fun `aggregateLevels returns empty for empty input`() {
        assertTrue(aggregateLevels(emptyList(), AggregationLevel.TenTick, tickSize = 1.0).isEmpty())
    }

    @Test
    fun `aggregateLevels merges prices by tick multiplier`() {
        val levels = listOf(
            level("101.0", bid = "1", ask = "0.5", bidCount = 1, askCount = 1),
            level("105.0", bid = "2", ask = "1.5", bidCount = 2, askCount = 2),
            level("109.0", bid = "3", ask = "2.5", bidCount = 3, askCount = 3),
        )

        val result = aggregateLevels(levels, AggregationLevel.TenTick, tickSize = 1.0)

        assertEquals(1, result.size)
        val merged = result.first()
        assertEquals("100", merged.price)
        assertEquals(6f, merged.bidVolumeFloat)
        assertEquals(4.5f, merged.askVolumeFloat)
        assertEquals(6, merged.bidCount)
        assertEquals(6, merged.askCount)
    }

    @Test
    fun `footprint level is visible when its price span intersects viewport`() {
        assertTrue(footprintLevelVisible(price = 90f, nextPrice = 101f, visibleMin = 100f, visibleMax = 110f))
        assertTrue(footprintLevelVisible(price = 109f, nextPrice = 120f, visibleMin = 100f, visibleMax = 110f))
        assertTrue(footprintLevelVisible(price = 105f, nextPrice = 106f, visibleMin = 100f, visibleMax = 110f))
    }

    @Test
    fun `footprint level is hidden outside viewport`() {
        assertFalse(footprintLevelVisible(price = 80f, nextPrice = 90f, visibleMin = 100f, visibleMax = 110f))
        assertFalse(footprintLevelVisible(price = 120f, nextPrice = 130f, visibleMin = 100f, visibleMax = 110f))
        assertFalse(footprintLevelVisible(price = Float.NaN, nextPrice = 105f, visibleMin = 100f, visibleMax = 110f))
    }

    @Test
    fun `profile scale uses the selected percentile`() {
        val scale = computeProfileVolumeScale(
            floatArrayOf(1f, 2f, 3f, 4f),
            percentileFraction = 0.9f,
        )

        assertEquals(3.7f, scale.linearMax, absoluteTolerance = 0.0001f)
        assertTrue(scale.isOutlier(4f))
        assertFalse(scale.isOutlier(3f))
    }

    @Test
    fun `profile scale MAX includes the largest level`() {
        val scale = computeProfileVolumeScale(
            floatArrayOf(1f, 2f, 3f, 100f),
            percentileFraction = 1f,
        )

        assertEquals(100f, scale.linearMax)
        assertFalse(scale.isOutlier(100f))
    }

    @Test
    fun `profile scale stays linear for uniform volumes`() {
        val scale = computeProfileVolumeScale(
            floatArrayOf(10f, 20f, 30f, 40f),
            percentileFraction = 1f,
        )

        assertEquals(40f, scale.linearMax)
        assertFalse(scale.isOutlier(40f))
    }

    @Test
    fun `profile scale ignores stale tail values`() {
        val values = floatArrayOf(1f, 2f, 3f, 100f, -1f, -1f, -1f, -1f)

        val scale = computeProfileVolumeScale(values, count = 4)

        assertTrue(scale.linearMax < 100f)
        assertTrue(scale.isOutlier(100f))
    }

    @Test
    fun `profile bar clamps outliers to full width`() {
        val scale = ProfileVolumeScale(linearMax = 3f, outlierThreshold = 3f)

        assertEquals(10f, profileBarWidth(100f, scale, maxBarWidth = 10f))
        assertEquals(6.6666665f, profileBarWidth(2f, scale, maxBarWidth = 10f), absoluteTolerance = 0.0001f)
        assertEquals(0f, profileBarWidth(0f, scale, maxBarWidth = 10f))
        assertEquals(1f, profileBarAlpha(100f, scale, minAlpha = 0.12f))
    }

    @Test
    fun `profile ring diameter scales from candle width`() {
        assertEquals(20f, profileRingDiameter(20f, FootprintRingDiameter.X1))
        assertEquals(30f, profileRingDiameter(20f, FootprintRingDiameter.X1_5))
        assertEquals(50f, profileRingDiameter(20f, FootprintRingDiameter.X2_5))
        assertEquals(60f, profileRingDiameter(20f, FootprintRingDiameter.X3))
        assertEquals(100f, profileRingDiameter(20f, FootprintRingDiameter.X5))
        assertEquals(200f, profileRingDiameter(20f, FootprintRingDiameter.X10))
    }

    @Test
    fun `profile ring presets support the extended range`() {
        assertEquals(5f, FootprintRingWidth.FIVE.width.value)
        assertEquals(FootprintRingWidth.FIVE, FootprintRingWidth.fromString("5"))
        assertEquals(10f, FootprintRingDiameter.X10.ratio)
        assertEquals(FootprintRingDiameter.X10, FootprintRingDiameter.fromString("10x"))
        assertEquals(FootprintRingDiameter.X5, FootprintRingDiameter.fromString("500%"))
    }

    @Test
    fun `aggregateLevels keeps distant price groups separate`() {
        val levels = listOf(
            level("101.0", bid = "1"),
            level("250.0", bid = "2"),
        )

        val result = aggregateLevels(levels, AggregationLevel.HundredTick, tickSize = 1.0)

        assertEquals(2, result.size)
        assertEquals(listOf("100", "200"), result.map { it.price })
        assertEquals(3f, result.sumOf { it.bidVolumeFloat.toDouble() }.toFloat())
    }

    private fun level(
        price: String,
        bid: String = "0",
        ask: String = "0",
        bidCount: Int = 0,
        askCount: Int = 0,
    ) = FootprintLevel(
        price = price,
        bidVolume = bid,
        askVolume = ask,
        bidCount = bidCount,
        askCount = askCount,
    )
}
