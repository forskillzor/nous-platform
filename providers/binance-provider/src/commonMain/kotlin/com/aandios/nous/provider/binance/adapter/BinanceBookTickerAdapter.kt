/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.provider.binance.currentTimeMillis
import com.aandios.nous.provider.binance.model.BinanceBookTicker
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay
import com.aandios.nous.api.market.model.BookTicker
import io.ktor.client.call.body
import io.ktor.client.request.get

class BinanceBookTickerAdapter(
    private val client: HttpClient,
    private val config: ProviderConfig,
): BookTickerAdapter {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun subscribeToBookTicker(symbol: String): Flow<BookTicker> = callbackFlow {
        val streamName = "${symbol.lowercase()}@bookTicker"
        val endpoint = "wss://fstream.binance.com/ws/$streamName"

        try {
            client.webSocket(urlString = endpoint) {

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()

                            try {
                                val binanceBookTicker = json.decodeFromString<BinanceBookTicker>(text)
                                trySend(binanceBookTicker.toBookTicker())

                            } catch (e: Exception) {
                                println("❌ BookTicker parse error: ${e.message}")
                            }
                        }
                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            // fixme: here is sometime ping timeout
            println("❌ BookTicker WebSocket failed: ${e.message}")
            e.printStackTrace()
            delay(1000)
            throw e
        }

        close()
    }
    override suspend fun getBookTickerRest(symbol: String): BookTicker? {
        return try {
            val response: Map<String, String> = client.get("https://fapi.binance.com/fapi/v1/ticker/bookTicker") {
                url {
                    parameters.append("symbol", symbol)
                }
            }.body()

            BinanceBookTicker(
                symbol = response["symbol"] ?: return null,
                bestBidPrice = response["bidPrice"] ?: "0",
                bestBidQty = response["bidQty"] ?: "0",
                bestAskPrice = response["askPrice"] ?: "0",
                bestAskQty = response["askQty"] ?: "0",
                eventTime = response["time"]?.toLong() ?: currentTimeMillis(),
            ).toBookTicker()
        } catch (e: Exception) {
            println("❌ Failed to get best price via REST: ${e.message}")
            null
        }
    }
}
