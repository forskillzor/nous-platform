/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.provider.binance.BinanceStreamHub
import com.aandios.nous.provider.binance.model.BBookWindow
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Binance DOM Adapter — partial-стримы стакана (без инкрементальной синхронизации).
 * Стримы мультиплексируются через общий [BinanceStreamHub] — N панелей
 * на одном символе/глубине используют одно соединение.
 */
class BinanceDomAdapter(
    private val client: HttpClient,
    private val config: ProviderConfig,
    private val streamHub: BinanceStreamHub,
) : DomAdapter {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun subscribeToBookWindow(symbol: String, depth: Int): Flow<BookWindowLevels> {
        val streamName = "${symbol.lowercase()}@depth${depth}@100ms"
        return streamHub.subscribe(streamName).map { text ->
            json.decodeFromString<BBookWindow>(text).toBookWindowLevels()
        }
    }
}
