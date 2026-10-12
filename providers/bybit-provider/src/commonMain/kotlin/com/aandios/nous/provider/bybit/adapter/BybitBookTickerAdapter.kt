/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit.adapter

import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.provider.bybit.BybitRestClient
import com.aandios.nous.provider.bybit.BybitStreamHub
import com.aandios.nous.provider.bybit.asArray
import com.aandios.nous.provider.bybit.asObject
import com.aandios.nous.provider.bybit.bybitBookTicker
import com.aandios.nous.provider.bybit.bybitCurrentTimeMillis
import com.aandios.nous.provider.bybit.long
import com.aandios.nous.provider.bybit.string
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class BybitBookTickerAdapter internal constructor(
    private val rest: BybitRestClient,
    private val streams: BybitStreamHub,
) : BookTickerAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    override fun subscribeToBookTicker(symbol: String): Flow<BookTicker> {
        val topic = "tickers.$symbol"
        return flow {
            var previous: BookTicker? = null
            streams.subscribe(topic).collect { text ->
                val root = json.parseToJsonElement(text).asObject() ?: return@collect
                if (root.string("topic") != topic) return@collect
                val row = root["data"]?.asObject() ?: return@collect
                val ticker = bybitBookTicker(row, root.long("ts") ?: bybitCurrentTimeMillis(), previous)
                    ?: return@collect
                previous = ticker
                emit(ticker)
            }
        }
    }

    override suspend fun getBookTickerRest(symbol: String): BookTicker? {
        val result = rest.get("/v5/market/tickers", mapOf("category" to "linear", "symbol" to symbol))
        val row = result["list"]?.asArray()?.firstOrNull()?.asObject() ?: return null
        return bybitBookTicker(row, result.long("ts") ?: bybitCurrentTimeMillis())
    }
}
