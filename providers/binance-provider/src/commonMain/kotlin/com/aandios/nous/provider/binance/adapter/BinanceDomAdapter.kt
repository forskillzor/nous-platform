/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.provider.binance.model.BBookWindow
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json

/**
 * Binance DOM Adapter — partial-стримы стакана (без инкрементальной синхронизации).
 *
 * Нативные окна существуют только для уровней 5/10/20 (`depth<levels>@100ms`) —
 * частичных стримов с большей глубиной на Binance Futures нет.
 */
class BinanceDomAdapter(
    private val client: HttpClient,
    private val config: ProviderConfig,
) : DomAdapter {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun subscribeToBookWindow(symbol: String, depth: Int): Flow<BookWindowLevels> = callbackFlow {
        val streamName = "${symbol.lowercase()}@depth${depth}@100ms"
        val endpoint = if (config.isTestnet) {
            "wss://testnet.binance.vision/ws/$streamName"
        } else {
            "wss://fstream.binance.com/ws/$streamName"
        }

        client.webSocket(urlString = endpoint) {
            println("✅ Book window WebSocket connected for $symbol (depth$depth@100ms)")

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val window = json.decodeFromString<BBookWindow>(frame.readText())
                    trySend(window.toBookWindowLevels())
                }
            }
        }

        close()
    }
}
