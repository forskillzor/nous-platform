/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.provider.hyperliquid.adapter.HyperliquidChartAdapter
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HyperliquidChartAdapterTest {
    private fun candles(): String = """
        [
          {"t":998200000,"o":"10","h":"12","l":"9","c":"11","v":"100"},
          {"t":997300000,"o":"9","h":"11","l":"8","c":"10","v":"90"},
          {"t":999100000,"o":"11","h":"13","l":"10","c":"12","v":"110"}
        ]
    """.trimIndent()

    @Test
    fun `loads candles with start and end for the requested interval`() = runTest {
        val info = HyperliquidFixtures.fakeInfo(
            mapOf("candleSnapshot" to jsonElement(candles())),
        )
        val marketData = HyperliquidFixtures.marketData(info)
        val adapter = HyperliquidChartAdapter(info, FakeHyperliquidStreams(), marketData) { 1_000_000_000L }

        val result = adapter.getCandles("BTC", "15m", 2)
        assertEquals(listOf(998_200_000L, 999_100_000L), result.map { it.timestamp })
        assertEquals(12.0f, result.last().close)

        val request = info.requests.last()
        assertEquals("candleSnapshot", request.string("type"))
        val req = request["req"]?.asObject() ?: error("missing req")
        assertEquals("BTC", req.string("coin"))
        assertEquals("15m", req.string("interval"))
        assertEquals(998_200_000L, req.long("startTime"))
        assertEquals(1_000_000_000L, req.long("endTime"))
    }

    @Test
    fun `loads history strictly before the requested timestamp`() = runTest {
        val info = HyperliquidFixtures.fakeInfo(
            mapOf("candleSnapshot" to jsonElement(candles())),
        )
        val marketData = HyperliquidFixtures.marketData(info)
        val adapter = HyperliquidChartAdapter(info, FakeHyperliquidStreams(), marketData) { 1_000_000_000L }

        val result = adapter.getCandlesBefore("BTC", "15m", 999_100_000L, 2)
        assertEquals(listOf(997_300_000L, 998_200_000L), result.map { it.timestamp })
        val req = info.requests.last()["req"]?.asObject() ?: error("missing req")
        assertEquals(999_099_999L, req.long("endTime"))
    }

    @Test
    fun `streams the newest candle from each websocket snapshot`() = runTest {
        val info = HyperliquidFixtures.fakeInfo()
        val marketData = HyperliquidFixtures.marketData(info)
        val streams = FakeHyperliquidStreams()
        val adapter = HyperliquidChartAdapter(info, streams, marketData)
        val flow = adapter.subscribeToCandles("BTC", "15m")
        val next = async { flow.first() }

        streams.emit(
            "candle|BTC|15m",
            jsonObject(
                """
                {"channel":"candle","data":[
                  {"t":1000,"o":"1","h":"2","l":"0.5","c":"1.5","v":"5"},
                  {"t":2000,"o":"2","h":"3","l":"1.5","c":"2.5","v":"6"}
                ]}
                """.trimIndent(),
            ),
        )

        assertEquals(2000L, next.await().timestamp)
        assertEquals(
            jsonObject("""{"type":"candle","coin":"BTC","interval":"15m"}"""),
            streams.subscription("candle|BTC|15m"),
        )
    }

    @Test
    fun `streams a single candle object from the live websocket`() = runTest {
        val info = HyperliquidFixtures.fakeInfo()
        val marketData = HyperliquidFixtures.marketData(info)
        val streams = FakeHyperliquidStreams()
        val adapter = HyperliquidChartAdapter(info, streams, marketData)
        val flow = adapter.subscribeToCandles("BTC", "15m")
        val next = async { flow.first() }

        streams.emit(
            "candle|BTC|15m",
            jsonObject(
                """
                {"channel":"candle","data":{
                  "t":3000,"T":3099,"s":"BTC","i":"15m",
                  "o":"2","c":"2.5","h":"3","l":"1","v":"6","n":2
                }}
                """.trimIndent(),
            ),
        )

        val candle = next.await()
        assertEquals(3000L, candle.timestamp)
        assertEquals(2.5f, candle.close)
    }
}
