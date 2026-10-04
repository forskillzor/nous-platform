/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance

import com.aandios.nous.api.market.ProviderConfig
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test

class StreamHubDiagnostics {

    private suspend fun probe(hub: BinanceStreamHub, name: String, timeoutMs: Long = 20_000): Boolean {
        val result = withTimeoutOrNull(timeoutMs) {
            hub.subscribe(name).take(1).toList().firstOrNull()
        }
        println("PROBE $name -> ${if (result != null) "OK (${result.take(110)})" else "NO DATA in ${timeoutMs}ms"}")
        return result != null
    }

    @Test
    fun hubDeliversAllStreamTypes() = runBlocking {
        val client = BinanceHttpClientFactory.create()
        val hub = BinanceStreamHub(client, ProviderConfig(isTestnet = false))

        println("=== kline_1m ===")
        probe(hub, "btcusdt@kline_1m")
        println("=== aggTrade ===")
        probe(hub, "btcusdt@aggTrade")
        println("=== depth10@100ms ===")
        probe(hub, "btcusdt@depth10@100ms")
        println("=== bookTicker ===")
        probe(hub, "btcusdt@bookTicker")
        println("=== forceOrder ===")
        probe(hub, "btcusdt@forceOrder", timeoutMs = 10_000)

        println("=== connections=${hub.connectionCount} streams=${hub.streamCount}")
        client.close()
    }
}
