/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.provider.mexc.adapter.MexcBookTickerAdapter
import com.aandios.nous.provider.mexc.adapter.MexcChartAdapter
import com.aandios.nous.provider.mexc.adapter.MexcDomAdapter
import com.aandios.nous.provider.mexc.adapter.MexcSymbolInfoAdapter
import com.aandios.nous.provider.mexc.adapter.MexcTradesAdapter
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test

/**
 * Живая диагностика MEXC Futures (печать, без ассертов — CI-безопасно):
 * REST-эндпоинты и WS-каналы хаба.
 */
class MexcProviderDiagnostics {

    @Test
    fun providerDeliversAllDataTypes() = runBlocking {
        val client = MexcHttpClientFactory.create()
        val config = ProviderConfig(displayName = "MEXC")
        val hub = MexcStreamHub(client, config)
        val gate = MexcRestGate()

        println("=== REST: kline ===")
        runCatching {
            MexcChartAdapter(client, config, gate, hub).getCandles("BTC_USDT", "5m", 5)
        }.onSuccess { println("OK candles=${it.size} last=${it.lastOrNull()}") }
            .onFailure { println("ERR ${it.message}") }

        println("=== REST: depth ===")
        runCatching {
            client.get("$MEXC_BASE_URL/api/v1/contract/depth/BTC_USDT").body<String>().take(120)
        }.onSuccess { println("OK $it") }.onFailure { println("ERR ${it.message}") }

        println("=== REST: deals ===")
        runCatching {
            MexcTradesAdapter(client, config, gate, hub).getRecentTrades("BTC_USDT", 5)
        }.onSuccess { println("OK deals=${it.size} first=${it.firstOrNull()}") }
            .onFailure { println("ERR ${it.message}") }

        println("=== REST: ticker ===")
        runCatching {
            MexcBookTickerAdapter(client, config, gate, hub).getBookTickerRest("BTC_USDT")
        }.onSuccess { println("OK $it") }.onFailure { println("ERR ${it.message}") }

        println("=== REST: contract detail ===")
        runCatching {
            MexcSymbolInfoAdapter(client, config, gate).getAllSymbolsInfo()
        }.onSuccess { println("OK contracts=${it.size} first=${it.firstOrNull()}") }
            .onFailure { println("ERR ${it.message}") }

        println("=== WS: kline ===")
        probe(hub, MexcSubscriptions.kline("BTC_USDT", "Min1"))
        println("=== WS: deal ===")
        probe(hub, MexcSubscriptions.deal("BTC_USDT"))
        println("=== WS: depth.full ===")
        probe(hub, MexcSubscriptions.depthFull("BTC_USDT"))
        println("=== WS: ticker ===")
        probe(hub, MexcSubscriptions.ticker("BTC_USDT"))

        println("=== WS: trades adapter flow ===")
        runCatching {
            val flow = MexcTradesAdapter(client, config, gate, hub).subscribeToTrades("BTC_USDT")
            withTimeoutOrNull(25_000) { flow.take(3).toList() }
        }.onSuccess { println(if (it != null) "OK trades=${it.size} first=${it.firstOrNull()}" else "NO DATA") }
            .onFailure { println("ERR ${it.message}") }

        println("=== WS: chart adapter flow ===")
        runCatching {
            val flow = MexcChartAdapter(client, config, gate, hub).subscribeToCandles("BTC_USDT", "1m")
            withTimeoutOrNull(25_000) { flow.take(2).toList() }
        }.onSuccess { println(if (it != null) "OK candles=${it.size} first=${it.firstOrNull()}" else "NO DATA") }
            .onFailure { println("ERR ${it.message}") }

        println("=== DOM adapter flow ===")
        runCatching {
            val flow = MexcDomAdapter(client, config, hub).subscribeToBookWindow("BTC_USDT", 5)
            withTimeoutOrNull(20_000) { flow.take(1).toList().firstOrNull() }
        }.onSuccess { println(if (it != null) "OK bids=${it.bids.size} asks=${it.asks.size}" else "NO DATA") }
            .onFailure { println("ERR ${it.message}") }

        println("=== connections=${hub.connectionCount} streams=${hub.streamCount}")
        client.close()
    }

    private suspend fun probe(hub: MexcStreamHub, sub: MexcSub, timeoutMs: Long = 25_000): Boolean {
        val result = withTimeoutOrNull(timeoutMs) {
            hub.subscribe(sub).take(1).toList().firstOrNull()
        }
        println("PROBE ${sub.key} -> ${if (result != null) "OK (${result.take(110)})" else "NO DATA in ${timeoutMs}ms"}")
        return result != null
    }
}
