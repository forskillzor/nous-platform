/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.provider.binance.model.BinanceForceOrderResponse
import com.aandios.nous.provider.binance.model.BinanceLiquidationEvent
import com.aandios.nous.provider.binance.model.toLiquidationOrder
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

class BinanceLiquidationAdapter(
    private val httpClient: HttpClient,
    private val config: ProviderConfig
) : LiquidationAdapter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun subscribeToLiquidations(symbol: String): Flow<LiquidationOrder> = callbackFlow {
        val streamName = "${symbol.lowercase()}@forceOrder"
        val endpoint = if (config.isTestnet) {
            "wss://testnet.binance.vision/ws/$streamName"
        } else {
            "wss://fstream.binance.com/market/ws/$streamName"
        }

        var retryDelay = 1_000L
        val maxRetryDelay = 30_000L

        while (isActive) {
            try {
                println("\uD83D\uDC80 Liquidation WebSocket: connecting to $streamName")
                retryDelay = 1_000L

                httpClient.webSocket(urlString = endpoint) {
                    println("\uD83D\uDC80 Liquidation WebSocket: connected to $streamName")
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            try {
                                val event = json.decodeFromString<BinanceLiquidationEvent>(text)
                                val liqOrder = event.order.toLiquidationOrder()
                                if (liqOrder.quantity > 0.0) {
                                    trySend(liqOrder)
                                }
                            } catch (e: Exception) {
                                println("⚠️ Liquidation parse error: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isActive) break
                println("❌ Liquidation WebSocket error: ${e.message}. Reconnecting in ${retryDelay}ms...")
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
            }
        }
        println("💀 Liquidation WebSocket: closed $streamName")
        close()
    }

    override suspend fun getHistoricalLiquidations(
        symbol: String,
        startTime: Long?,
        endTime: Long?,
        limit: Int
    ): List<LiquidationOrder> {
        val endpoint = if (config.isTestnet) {
            "https://testnet.binancefuture.com/fapi/v1/allForceOrders"
        } else {
            "https://fapi.binance.com/fapi/v1/allForceOrders"
        }
        return try {
            val response: List<BinanceForceOrderResponse> = httpClient.get(endpoint) {
                parameter("symbol", symbol)
                startTime?.let { parameter("startTime", it) }
                endTime?.let { parameter("endTime", it) }
                parameter("limit", limit.coerceAtMost(1000))
            }.body()
            response.map { it.toLiquidationOrder() }.filter { it.quantity > 0.0 }
        } catch (e: Exception) {
            println("⚠️ Liquidation history fetch failed: ${e.message}")
            emptyList()
        }
    }
}
