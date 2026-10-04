/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.provider.binance.BinanceRestGate
import com.aandios.nous.provider.binance.BinanceStreamHub
import com.aandios.nous.provider.binance.model.BinanceForceOrderResponse
import com.aandios.nous.provider.binance.model.BinanceLiquidationEvent
import com.aandios.nous.provider.binance.model.toLiquidationOrder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json

class BinanceLiquidationAdapter(
    private val httpClient: HttpClient,
    private val config: ProviderConfig,
    private val restGate: BinanceRestGate,
    private val streamHub: BinanceStreamHub,
) : LiquidationAdapter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun subscribeToLiquidations(symbol: String): Flow<LiquidationOrder> {
        // Глобальный стрим всех ликвидаций — hub дедуплицирует его между панелями.
        val streamName = "${symbol.lowercase()}@forceOrder"
        return streamHub.subscribe(streamName).mapNotNull { text ->
            runCatching {
                val event = json.decodeFromString<BinanceLiquidationEvent>(text)
                val liqOrder = event.order.toLiquidationOrder()
                liqOrder.takeIf { it.quantity > 0.0 }
            }.getOrNull()
        }
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
            restGate.execute(key = "allForceOrders:$symbol:$startTime:$endTime:$limit", weight = 20) {
                val response: List<BinanceForceOrderResponse> = httpClient.get(endpoint) {
                    parameter("symbol", symbol)
                    startTime?.let { parameter("startTime", it) }
                    endTime?.let { parameter("endTime", it) }
                    parameter("limit", limit.coerceAtMost(1000))
                }.body()
                response.map { it.toLiquidationOrder() }.filter { it.quantity > 0.0 }
            }
        } catch (e: Exception) {
            println("⚠️ Liquidation history fetch failed: ${e.message}")
            emptyList()
        }
    }
}
