/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering

import com.aandios.nous.api.market.model.FootprintLevel
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
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
