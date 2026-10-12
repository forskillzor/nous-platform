/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit.adapter

import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.provider.bybit.BybitRestClient
import com.aandios.nous.provider.bybit.BybitStreamHub
import com.aandios.nous.provider.bybit.asArray
import com.aandios.nous.provider.bybit.asObject
import com.aandios.nous.provider.bybit.asObjectList
import com.aandios.nous.provider.bybit.bybitTrade
import com.aandios.nous.provider.bybit.string
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json

class BybitTradesAdapter internal constructor(
    private val rest: BybitRestClient,
    private val streams: BybitStreamHub,
) : TradesAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    override fun subscribeToTrades(symbol: String): Flow<Trade> {
        val topic = "publicTrade.$symbol"
        return streams.subscribe(topic).mapNotNull { text ->
            val root = json.parseToJsonElement(text).asObject() ?: return@mapNotNull null
            if (root.string("topic") != topic) return@mapNotNull null
            root["data"]?.asObjectList()?.firstNotNullOfOrNull(::bybitTrade)
        }
    }

    override suspend fun getRecentTrades(symbol: String, limit: Int): List<Trade> {
        val result = rest.get(
            "/v5/market/recent-trade",
            mapOf("category" to "linear", "symbol" to symbol, "limit" to limit.coerceIn(1, 1_000).toString()),
        )
        return result["list"]?.asObjectList().orEmpty().mapNotNull(::bybitTrade)
    }
}
