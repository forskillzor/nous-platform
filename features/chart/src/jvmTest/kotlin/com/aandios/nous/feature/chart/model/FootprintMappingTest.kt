/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.model

import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.FootprintLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class FootprintMappingTest {

    @Test
    fun `toSkeletonCandle maps footprint fields to candle`() {
        val footprint = FootprintCandle(
            exchange = "Binance",
            symbol = "BTCUSDT",
            timeframe = "1m",
            startTime = 1_000L,
            endTime = 61_000L,
            totalTicks = 5L,
            minPrice = "99.0",
            maxPrice = "101.0",
            levels = listOf(
                FootprintLevel(price = "101.0", bidVolume = "1", askVolume = "2"),
                FootprintLevel(price = "100.0", bidVolume = "3", askVolume = "0"),
            ),
        )

        val candle = footprint.toSkeletonCandle()

        assertEquals(101f, candle.open) // первый уровень
        assertEquals(101f, candle.high)
        assertEquals(100f, candle.close) // последний уровень
        assertEquals(99f, candle.low)
        assertEquals(1_000L, candle.timestamp)
        assertEquals(3f, candle.volume) // maxVolume
    }

    @Test
    fun `toSkeletonCandle falls back to levels when min and max are missing`() {
        val footprint = FootprintCandle(
            exchange = "Binance",
            symbol = "BTCUSDT",
            timeframe = "1m",
            startTime = 1_000L,
            endTime = 61_000L,
            minPrice = "0",
            maxPrice = "0",
            levels = listOf(
                FootprintLevel(price = "99.0", bidVolume = "1", askVolume = "0"),
                FootprintLevel(price = "101.0", bidVolume = "0", askVolume = "1"),
            ),
        )

        val candle = footprint.toSkeletonCandle()

        assertEquals(101f, candle.high)
        assertEquals(99f, candle.low)
        assertEquals(99f, candle.open)
        assertEquals(101f, candle.close)
    }
}
