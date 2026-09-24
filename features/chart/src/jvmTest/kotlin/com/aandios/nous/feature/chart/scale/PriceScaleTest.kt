/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.scale

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.feature.chart.utils.calculatePriceRangeWithCurrentPrice
import kotlin.test.Test
import kotlin.test.assertEquals

class PriceScaleTest {

    private fun candles() = listOf(
        Candle(open = 95f, high = 100f, close = 99f, low = 94f, timestamp = 0L),
        Candle(open = 99f, high = 105f, close = 104f, low = 98f, timestamp = 60_000L),
    )

    @Test
    fun `fit computes base range from provider`() {
        val scale = PriceScale()
        scale.fit { calculatePriceRangeWithCurrentPrice(candles(), currentPrice = null) }

        val range = scale.range(verticalScroll = 0f, chartHeight = 200f)

        assertEquals(105f, range.max, 0.001f)
        assertEquals(94f, range.min, 0.001f)
        assertEquals(105.55f, range.visibleMax, 0.001f)
        assertEquals(93.45f, range.visibleMin, 0.001f)
    }

    @Test
    fun `range applies vertical shift proportionally`() {
        val scale = PriceScale()
        scale.fit { calculatePriceRangeWithCurrentPrice(candles(), currentPrice = null) }
        val base = calculatePriceRangeWithCurrentPrice(candles(), currentPrice = null)

        val range = scale.range(verticalScroll = 100f, chartHeight = 200f)

        assertEquals(base.max + base.range * 0.5f, range.max, 0.001f)
        assertEquals(base.min + base.range * 0.5f, range.min, 0.001f)
        assertEquals(base.range, range.range, 0.001f)
    }

    @Test
    fun `range with zero height returns base`() {
        val scale = PriceScale()
        scale.fit { calculatePriceRangeWithCurrentPrice(candles(), currentPrice = null) }

        assertEquals(
            scale.range(verticalScroll = 0f, chartHeight = 100f),
            scale.range(verticalScroll = 500f, chartHeight = 0f),
        )
    }

    @Test
    fun `fit is idempotent for the same visible data`() {
        val scale = PriceScale()
        scale.fit { calculatePriceRangeWithCurrentPrice(candles(), currentPrice = null) }
        val first = scale.range(0f, 200f)
        scale.fit { calculatePriceRangeWithCurrentPrice(candles(), currentPrice = null) }
        val second = scale.range(0f, 200f)

        assertEquals(first, second)
    }
}
