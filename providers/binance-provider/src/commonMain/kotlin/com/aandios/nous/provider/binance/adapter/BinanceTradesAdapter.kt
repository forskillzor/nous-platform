/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.provider.binance.BinanceStreamHub
import com.aandios.nous.provider.binance.model.BinanceAggTrade
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class BinanceTradesAdapter(
    private val httpClient: HttpClient,
    private val config: ProviderConfig,
    private val streamHub: BinanceStreamHub,
) : TradesAdapter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun subscribeToTrades(symbol: String): Flow<Trade> {
        val streamName = "${symbol.lowercase()}@aggTrade"
        return streamHub.subscribe(streamName).map { text ->
            json.decodeFromString<BinanceAggTrade>(text).toTrade()
        }
    }

    override suspend fun getRecentTrades(symbol: String, limit: Int): List<Trade> {
        return emptyList() // Implement if needed
    }
}
