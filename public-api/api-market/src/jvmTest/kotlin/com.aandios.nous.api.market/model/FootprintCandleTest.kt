/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FootprintCandleTest {

    private val candle = FootprintCandle(
        exchange = "Binance",
        symbol = "BTCUSDT",
        timeframe = "1m",
        startTime = 1_000L,
        endTime = 61_000L,
        totalTicks = 12L,
        minPrice = "99.5",
        maxPrice = "101.5",
        levels = listOf(
            FootprintLevel(price = "99.5", bidVolume = "1.25", askVolume = "0.5"),
            FootprintLevel(price = "101.5", bidVolume = "3.75", askVolume = "4.5"),
        ),
    )

    @Test
    fun `level float caches match string fields`() {
        val level = FootprintLevel(price = "123.45", bidVolume = "6.5", askVolume = "0.25")

        assertEquals(123.45f, level.priceFloat)
        assertEquals(6.5f, level.bidVolumeFloat)
        assertEquals(0.25f, level.askVolumeFloat)
    }

    @Test
    fun `invalid numeric strings fall back to zero`() {
        val level = FootprintLevel(price = "oops", bidVolume = "", askVolume = "n/a")

        assertEquals(0f, level.priceFloat)
        assertEquals(0f, level.bidVolumeFloat)
        assertEquals(0f, level.askVolumeFloat)
    }

    @Test
    fun `candle float caches match levels and min max prices`() {
        assertEquals(99.5f, candle.open)
        assertEquals(99.5f, candle.low)
        assertEquals(101.5f, candle.high)
        assertEquals(101.5f, candle.close)
        assertEquals(4.5f, candle.maxVolume)
    }

    @Test
    fun `json roundtrip preserves public fields and recomputes caches`() {
        val encoded = Json.encodeToString(FootprintCandle.serializer(), candle)
        val decoded = Json.decodeFromString(FootprintCandle.serializer(), encoded)

        assertEquals(candle, decoded)
        assertEquals(candle.open, decoded.open)
        assertEquals(candle.high, decoded.high)
        assertEquals(candle.low, decoded.low)
        assertEquals(candle.close, decoded.close)
        assertEquals(candle.maxVolume, decoded.maxVolume)
    }
}
