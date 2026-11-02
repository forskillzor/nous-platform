/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.provider.hyperliquid.adapter.HyperliquidTradesAdapter
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HyperliquidTradesAdapterTest {
    @Test
    fun `maps recent trades`() = runTest {
        val info = HyperliquidFixtures.fakeInfo(
            mapOf(
                "recentTrades" to jsonElement(
                    """
                    [
                      {"coin":"BTC","side":"B","px":"100.5","sz":"1.25","time":10,"tid":7},
                      {"coin":"BTC","side":"A","px":"100.0","sz":"0.5","time":9,"tid":6}
                    ]
                    """.trimIndent(),
                ),
            ),
        )
        val adapter = HyperliquidTradesAdapter(info, FakeHyperliquidStreams())

        val trades = adapter.getRecentTrades("BTC", 10)
        assertEquals(2, trades.size)
        assertEquals(100.5, trades[0].price)
        assertEquals(TradeSide.BUY, trades[0].side)
        assertFalse(trades[0].isBuyerMaker)
        assertEquals(TradeSide.SELL, trades[1].side)
        assertTrue(trades[1].isBuyerMaker)
        assertEquals(7L, trades[0].id)
        assertEquals("recentTrades", info.requests.single().string("type"))
    }

    @Test
    fun `streams trades from the websocket`() = runTest {
        val info = HyperliquidFixtures.fakeInfo()
        val streams = FakeHyperliquidStreams()
        val adapter = HyperliquidTradesAdapter(info, streams)
        val next = async { adapter.subscribeToTrades("BTC").first() }

        streams.emit(
            "trades|BTC",
            jsonObject(
                """{"channel":"trades","data":[{"coin":"BTC","side":"A","px":"99","sz":"2","time":11,"tid":42}]}""",
            ),
        )

        val trade = next.await()
        assertEquals(99.0, trade.price)
        assertEquals(TradeSide.SELL, trade.side)
        assertEquals(42L, trade.id)
    }
}
