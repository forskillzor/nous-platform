/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BybitMarketMappingTest {
    @Test
    fun `maps chart intervals to Bybit V5 intervals`() {
        assertEquals("1", bybitInterval("1m"))
        assertEquals("60", bybitInterval("1h"))
        assertEquals("D", bybitInterval("1d"))
        assertEquals("M", bybitInterval("1M"))
    }

    @Test
    fun `maps reverse ordered REST kline row into candle`() {
        val row = Json.parseToJsonElement(
            """["1720000000000","100.1","101.0","99.5","100.5","12.25","1231.0"]"""
        ).jsonArray

        val candle = bybitKline(row)

        assertNotNull(candle)
        assertEquals(1_720_000_000_000L, candle.timestamp)
        assertEquals(100.1f, candle.open)
        assertEquals(12.25f, candle.volume)
    }

    @Test
    fun `maps Bybit websocket kline object into candle`() {
        val candle = bybitKline(
            Json.parseToJsonElement(
                """{"start":"1720000000000","open":"100","high":"102","low":"99","close":"101","volume":"12.5"}"""
            ).jsonObject
        )

        assertNotNull(candle)
        assertEquals(1_720_000_000_000L, candle.timestamp)
        assertEquals(101f, candle.close)
    }

    @Test
    fun `maps public trade side and maker direction`() {
        val trade = bybitTrade(
            Json.parseToJsonElement(
                """{"i":"exec-1","s":"BTCUSDT","p":"62000.5","v":"0.02","T":"1720000000000","S":"Sell"}"""
            ).jsonObject
        )

        assertNotNull(trade)
        assertEquals(TradeSide.SELL, trade.side)
        assertEquals(true, trade.isBuyerMaker)
        assertEquals(62_000.5, trade.price)
    }

    @Test
    fun `maps liquidation event and order book depths`() {
        val liquidation = bybitLiquidation(
            Json.parseToJsonElement(
                """{"s":"BTCUSDT","S":"Buy","v":"0.5","p":"62000","T":"1720000000000"}"""
            ).jsonObject
        )

        assertNotNull(liquidation)
        assertEquals(TradeSide.BUY, liquidation.side)
        assertEquals(0.5, liquidation.quantity)
        assertEquals(1, bybitBookDepth(1))
        assertEquals(50, bybitBookDepth(20))
        assertEquals(200, bybitBookDepth(120))
    }

    @Test
    fun `rejects malformed kline and trade rows`() {
        assertNull(bybitKline(Json.parseToJsonElement("""["bad"]""").jsonArray))
        assertNull(bybitTrade(Json.parseToJsonElement("""{"s":"BTCUSDT"}""").jsonObject))
    }
}
