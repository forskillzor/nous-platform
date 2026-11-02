/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.provider.hyperliquid.adapter.HyperliquidBookTickerAdapter
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HyperliquidBookTickerAdapterTest {
    @Test
    fun `streams bbo snapshots with a mid last price`() = runTest {
        val streams = FakeHyperliquidStreams()
        val adapter = HyperliquidBookTickerAdapter(
            HyperliquidFixtures.fakeInfo(),
            streams,
            HyperliquidFixtures.marketData(),
        )
        val next = async { adapter.subscribeToBookTicker("BTC").first() }

        streams.emit(
            "bbo|BTC",
            jsonObject(
                """
                {"channel":"bbo","data":{"coin":"BTC","time":123,
                  "bbo":[{"px":"100","sz":"1.5"},{"px":"101","sz":"2.5"}]}}
                """.trimIndent(),
            ),
        )

        val ticker = next.await()
        assertEquals(100.0, ticker.bestBid)
        assertEquals(1.5, ticker.bestBidQty)
        assertEquals(101.0, ticker.bestAsk)
        assertEquals(2.5, ticker.bestAskQty)
        assertEquals(100.5, ticker.lastPrice)
        assertEquals(123L, ticker.timestamp)
    }

    @Test
    fun `loads the best bid and ask from the l2 book`() = runTest {
        val info = HyperliquidFixtures.fakeInfo(
            mapOf(
                "l2Book" to jsonElement(
                    """
                    {"coin":"BTC","time":55,"levels":[
                      [{"px":"99","sz":"1"}],
                      [{"px":"100","sz":"2"}]
                    ]}
                    """.trimIndent(),
                ),
            ),
        )
        val adapter = HyperliquidBookTickerAdapter(
            info,
            FakeHyperliquidStreams(),
            HyperliquidFixtures.marketData(info),
        )

        val ticker = adapter.getBookTickerRest("BTC") ?: error("no ticker")
        assertEquals(99.0, ticker.bestBid)
        assertEquals(100.0, ticker.bestAsk)
        assertEquals(99.5, ticker.lastPrice)
        assertEquals(55L, ticker.timestamp)
    }
}
