/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CandlesExtTest {

    private val candles = testCandles(10)

    @Test
    fun emptyListHasNoBucket() {
        assertNull(emptyList<com.aandios.nous.api.market.model.Candle>()
            .indexOfBucket(TEST_CANDLE_START, TEST_CANDLE_INTERVAL))
    }

    @Test
    fun timestampBeforeFirstCandleHasNoBucket() {
        assertNull(candles.indexOfBucket(TEST_CANDLE_START - 1, TEST_CANDLE_INTERVAL))
    }

    @Test
    fun firstTimestampMapsToFirstCandle() {
        assertEquals(0, candles.indexOfBucket(TEST_CANDLE_START, TEST_CANDLE_INTERVAL))
    }

    @Test
    fun timestampInsideBucketMapsToItsCandle() {
        val timestamp = candles[3].timestamp + TEST_CANDLE_INTERVAL / 2

        assertEquals(3, candles.indexOfBucket(timestamp, TEST_CANDLE_INTERVAL))
    }

    @Test
    fun exactCandleBoundaryMapsToThatCandle() {
        assertEquals(4, candles.indexOfBucket(candles[4].timestamp, TEST_CANDLE_INTERVAL))
    }

    @Test
    fun timestampAtOrAfterLastCandleMapsToLastIndex() {
        assertEquals(
            candles.lastIndex,
            candles.indexOfBucket(candles.last().timestamp, TEST_CANDLE_INTERVAL),
        )
        assertEquals(
            candles.lastIndex,
            candles.indexOfBucket(candles.last().timestamp + 1, TEST_CANDLE_INTERVAL),
        )
    }

    @Test
    fun nonPositiveTimeframeHasNoBucket() {
        assertNull(candles.indexOfBucket(TEST_CANDLE_START, 0))
        assertNull(candles.indexOfBucket(TEST_CANDLE_START, -1))
    }
}
