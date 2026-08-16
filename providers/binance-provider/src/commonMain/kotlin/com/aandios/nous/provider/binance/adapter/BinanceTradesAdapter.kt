/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.provider.binance.model.BinanceAggTrade
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

class BinanceTradesAdapter(
    private val httpClient: HttpClient,
    private val config: ProviderConfig
) : TradesAdapter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun subscribeToTrades(symbol: String): Flow<Trade> = callbackFlow {
        val streamName = "${symbol.lowercase()}@aggTrade"
        val endpoint = if (config.isTestnet) {
            "wss://testnet.binance.vision/ws/$streamName"
        } else {
            "wss://fstream.binance.com/market/ws/$streamName"
        }

        var retryDelay = 1_000L
        val maxRetryDelay = 30_000L

        while (isActive) {
            try {
                println("📊 Trades WebSocket: connecting to $streamName")
                retryDelay = 1_000L // reset on successful connection

                httpClient.webSocket(urlString = endpoint) {
                    println("📊 Trades WebSocket: connected to $streamName")
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            try {
                                val aggTrade = json.decodeFromString<BinanceAggTrade>(text)
                                trySend(aggTrade.toTrade())
                            } catch (e: Exception) {
                                println("⚠️ Trades parse error: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isActive) break
                println("❌ Trades WebSocket error: ${e.message}. Reconnecting in ${retryDelay}ms...")
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
            }
        }
        println("📊 Trades WebSocket: closed $streamName")
        close()
    }

    override suspend fun getRecentTrades(symbol: String, limit: Int): List<Trade> {
        return emptyList() // Implement if needed
    }
}