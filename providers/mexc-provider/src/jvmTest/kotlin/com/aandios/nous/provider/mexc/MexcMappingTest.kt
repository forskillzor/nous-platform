/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.provider.mexc.model.MexcContractDetail
import com.aandios.nous.provider.mexc.model.MexcDeal
import com.aandios.nous.provider.mexc.model.MexcDepthData
import com.aandios.nous.provider.mexc.model.MexcKlineData
import com.aandios.nous.provider.mexc.model.MexcKlineResponse
import com.aandios.nous.provider.mexc.model.MexcTickerData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MexcMappingTest {

    @Test
    fun intervalMapping() {
        assertEquals("Min1", MexcTimeframes.toMexc("1m"))
        assertEquals("Min5", MexcTimeframes.toMexc("5m"))
        assertEquals("Min15", MexcTimeframes.toMexc("15m"))
        assertEquals("Min30", MexcTimeframes.toMexc("30m"))
        assertEquals("Min60", MexcTimeframes.toMexc("1h"))
        assertEquals("Hour4", MexcTimeframes.toMexc("4h"))
        assertEquals("Day1", MexcTimeframes.toMexc("1d"))
        assertEquals("Week1", MexcTimeframes.toMexc("1w"))
        assertEquals("Min1", MexcTimeframes.toMexc("unknown"))
    }

    @Test
    fun timeToMillisConvertsSeconds() {
        assertEquals(1_790_574_900_000L, MexcTimeframes.timeToMillis(1_790_574_900L))
        assertEquals(1_791_174_875_439L, MexcTimeframes.timeToMillis(1_791_174_875_439L))
        assertEquals(0L, MexcTimeframes.timeToMillis(0L))
    }

    @Test
    fun klineParallelArraysToCandles() {
        val response = MexcKlineResponse(
            success = true,
            data = MexcKlineData(
                time = listOf(1_790_574_900L, 1_790_575_200L),
                open = listOf(100.0, 101.0),
                high = listOf(105.0, 106.0),
                low = listOf(99.0, 100.5),
                close = listOf(104.0, 103.0),
                vol = listOf(1000.0, 2000.0),
            )
        )
        val candles = response.toCandles()
        assertEquals(2, candles.size)
        assertEquals(1_790_574_900_000L, candles[0].timestamp)
        assertEquals(100f, candles[0].open)
        assertEquals(105f, candles[0].high)
        assertEquals(99f, candles[0].low)
        assertEquals(104f, candles[0].close)
        assertEquals(1000f, candles[0].volume)
    }

    @Test
    fun dealToTrade() {
        val buy = MexcDeal(p = 85571.0, v = 107.0, T = 1, t = 1_791_174_875_439L, i = "16484111211")
        val buyTrade = buy.toTrade("BTC_USDT", 1L)
        assertEquals(16484111211L, buyTrade.id)
        assertEquals(TradeSide.BUY, buyTrade.side)
        assertEquals(false, buyTrade.isBuyerMaker)

        val sell = MexcDeal(p = 85571.0, v = 2.0, T = 2, t = 1_791_174_875_439L)
        val sellTrade = sell.toTrade("BTC_USDT", 42L)
        assertEquals(42L, sellTrade.id) // нет i → fallback
        assertEquals(TradeSide.SELL, sellTrade.side)
        assertEquals(true, sellTrade.isBuyerMaker)
    }

    @Test
    fun depthToLevels() {
        val depth = MexcDepthData(
            asks = listOf(listOf(85570.4, 150436.0, 1.0), listOf(85570.5, 4704.0)),
            bids = listOf(listOf(85570.2, 9000.0)),
        )
        val levels = depth.toBookWindowLevels()
        assertEquals(2, levels.asks.size)
        assertEquals(85570.4, levels.asks[0].price)
        assertEquals(150436.0, levels.asks[0].quantity)
        assertEquals(1, levels.bids.size)
        assertEquals(85570.2, levels.bids[0].price)
    }

    @Test
    fun tickerToBookTicker() {
        val ticker = MexcTickerData(
            symbol = "BTC_USDT", lastPrice = 85578.7, bid1 = 85578.6, ask1 = 85578.7,
            timestamp = 1_791_174_875_439L,
        )
        val bt = ticker.toBookTicker()
        assertEquals("BTC_USDT", bt.symbol)
        assertEquals(85578.6, bt.bestBid)
        assertEquals(85578.7, bt.bestAsk)
        assertEquals(85578.7, bt.lastPrice)
    }

    @Test
    fun contractDetailToSymbolInfo() {
        val detail = MexcContractDetail(
            symbol = "BTC_USDT", baseCoin = "BTC", quoteCoin = "USDT", settleCoin = "USDT",
            priceUnit = 0.1, volUnit = 1.0, minVol = 1.0, state = 0,
        )
        val info = detail.toSymbolInfo()
        assertEquals("BTC_USDT", info.symbol)
        assertEquals(0.1, info.tickSize)
        assertEquals(1.0, info.stepSize)
        assertEquals(1.0, info.minQty)
        assertEquals("TRADING", info.status)
        assertEquals("PERPETUAL", info.contractType)

        val halted = detail.copy(state = 2).toSymbolInfo()
        assertEquals("HALT", halted.status)
    }

    @Test
    fun subscriptionsJson() {
        val kline = MexcSubscriptions.kline("BTC_USDT", "Min5")
        assertTrue(kline.key == "kline:BTC_USDT:Min5")
        assertTrue(kline.subscribeJson.contains("sub.kline"))
        assertTrue(kline.subscribeJson.contains("BTC_USDT"))
        assertTrue(kline.unsubscribeJson.contains("unsub.kline"))

        assertTrue(MexcSubscriptions.deal("BTC_USDT").subscribeJson.contains("sub.deal"))
        assertTrue(MexcSubscriptions.depthFull("BTC_USDT").subscribeJson.contains("sub.depth.full"))
        assertTrue(MexcSubscriptions.ticker("BTC_USDT").subscribeJson.contains("sub.ticker"))
    }
}
