/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.provider.binance.BinanceRestGate
import com.aandios.nous.provider.binance.BinanceStreamHub
import com.aandios.nous.provider.binance.currentTimeMillis
import com.aandios.nous.provider.binance.model.BinanceBookTicker
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class BinanceBookTickerAdapter(
    private val client: HttpClient,
    private val config: ProviderConfig,
    private val restGate: BinanceRestGate,
    private val streamHub: BinanceStreamHub,
) : BookTickerAdapter {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun subscribeToBookTicker(symbol: String): Flow<BookTicker> {
        val streamName = "${symbol.lowercase()}@bookTicker"
        return streamHub.subscribe(streamName).map { text ->
            json.decodeFromString<BinanceBookTicker>(text).toBookTicker()
        }
    }

    override suspend fun getBookTickerRest(symbol: String): BookTicker? {
        return try {
            restGate.execute(key = "bookTicker:$symbol", weight = 1) {
                val response: Map<String, String> = client.get("https://fapi.binance.com/fapi/v1/ticker/bookTicker") {
                    url {
                        parameters.append("symbol", symbol)
                    }
                }.body()

                BinanceBookTicker(
                    symbol = response["symbol"] ?: return@execute null,
                    bestBidPrice = response["bidPrice"] ?: "0",
                    bestBidQty = response["bidQty"] ?: "0",
                    bestAskPrice = response["askPrice"] ?: "0",
                    bestAskQty = response["askQty"] ?: "0",
                    eventTime = response["time"]?.toLong() ?: currentTimeMillis(),
                ).toBookTicker()
            }
        } catch (e: Exception) {
            println("❌ Failed to get best price via REST: ${e.message}")
            null
        }
    }
}
