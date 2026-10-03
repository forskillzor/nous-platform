/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainModelsTest {

    @Test
    fun `DepthLimit create and validation`() {
        // Default value
        val default = DepthLimit.default()
        assertEquals(20, default.value)

        // Create with valid value
        val limit = DepthLimit.create(10)
        assertEquals(10, limit.value)

        // Coercion to min/max
        val tooLow = DepthLimit.create(1)
        assertEquals(5, tooLow.value) // MIN_VALUE = 5
        val tooHigh = DepthLimit.create(2000)
        assertEquals(20, tooHigh.value) // MAX_VALUE = 20

        // Standard values check
        assertTrue(DepthLimit.create(5).isStandard())
        assertTrue(DepthLimit.create(10).isStandard())
        assertTrue(DepthLimit.create(20).isStandard())
        assertFalse(DepthLimit(15).isStandard())
    }

    @Test
    fun `AggregationLevel all and fromString`() {
        val all = AggregationLevel.all()
        assertEquals(3, all.size)
        assertTrue(all.contains(AggregationLevel.BaseTick))
        assertTrue(all.contains(AggregationLevel.TenTick))
        assertTrue(all.contains(AggregationLevel.HundredTick))

        // fromString
        assertEquals(AggregationLevel.BaseTick, AggregationLevel.fromString("BaseTick"))
        assertEquals(AggregationLevel.BaseTick, AggregationLevel.fromString("1×"))
        assertEquals(AggregationLevel.BaseTick, AggregationLevel.fromString("1x"))
        assertEquals(AggregationLevel.BaseTick, AggregationLevel.fromString("1.0"))

        assertEquals(AggregationLevel.TenTick, AggregationLevel.fromString("TenTick"))
        assertEquals(AggregationLevel.TenTick, AggregationLevel.fromString("10×"))
        assertEquals(AggregationLevel.TenTick, AggregationLevel.fromString("10x"))
        assertEquals(AggregationLevel.TenTick, AggregationLevel.fromString("10.0"))

        assertEquals(AggregationLevel.HundredTick, AggregationLevel.fromString("HundredTick"))
        assertEquals(AggregationLevel.HundredTick, AggregationLevel.fromString("100×"))
        assertEquals(AggregationLevel.HundredTick, AggregationLevel.fromString("100x"))
        assertEquals(AggregationLevel.HundredTick, AggregationLevel.fromString("100.0"))
    }

    @Test
    fun `AggregationLevel effectiveTickSize and roundDown`() {
        val baseTickSize = 0.01

        assertEquals(0.01, AggregationLevel.BaseTick.effectiveTickSize(baseTickSize))
        assertEquals(0.1, AggregationLevel.TenTick.effectiveTickSize(baseTickSize))
        assertEquals(1.0, AggregationLevel.HundredTick.effectiveTickSize(baseTickSize))

        // Round down
        assertEquals(123.45, AggregationLevel.BaseTick.roundDown(123.456, baseTickSize))
        assertEquals(123.4, AggregationLevel.TenTick.roundDown(123.456, baseTickSize))
        assertEquals(123.0, AggregationLevel.HundredTick.roundDown(123.456, baseTickSize))
    }

    @Test
    fun `OrderIntent subclasses and toOrderData`() {
        val marketBuy = OrderIntent.MarketBuy("BTCUSDT", 0.5)
        assertEquals("BTCUSDT", marketBuy.symbol)
        assertEquals(0.5, marketBuy.quantity)

        val limitSell = OrderIntent.LimitSell("ETHUSDT", 3500.0, 1.2)
        assertEquals("ETHUSDT", limitSell.symbol)
        assertEquals(3500.0, limitSell.price)
        assertEquals(1.2, limitSell.quantity)

        val bestBidBuy = OrderIntent.BestBidBuy("BTCUSDT", 50000.0, 0.1)
        assertEquals(50000.0, bestBidBuy.bestBidPrice)

        val toggle = OrderIntent.ToggleTrading
        assertEquals(null, toggle.toOrderData())
    }
}