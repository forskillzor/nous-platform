/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.indicator

import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.core.network.MarketDataServer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** Один агрегированный бакет ликвидаций из market-data-server. */
data class LiquidationBucket(
    val startTime: Long,
    val endTime: Long,
    val orders: List<LiquidationOrder>,
)

/** История ликвидаций из market-data-server (агрегаты по таймфрейму). */
interface LiquidationHistorySource {
    suspend fun getAggregates(
        exchange: String,
        symbol: String,
        timeframe: String,
        from: Long,
        to: Long,
        limit: Int,
    ): List<LiquidationBucket>
}

class MarketDataLiquidationSource(
    private val httpClient: HttpClient,
    private val baseUrl: String = MarketDataServer.BASE_URL,
) : LiquidationHistorySource {

    override suspend fun getAggregates(
        exchange: String,
        symbol: String,
        timeframe: String,
        from: Long,
        to: Long,
        limit: Int,
    ): List<LiquidationBucket> {
        val response = httpClient.get("$baseUrl/api/liquidation-aggregates") {
            parameter("exchange", exchange)
            parameter("symbol", symbol)
            parameter("timeframe", timeframe)
            parameter("from", from)
            parameter("to", to)
            parameter("limit", limit.coerceIn(1, MAX_BUCKETS))
            parameter("order", "asc")
        }
        if (!response.status.isSuccess()) {
            val detail = runCatching { response.bodyAsText() }.getOrDefault("").take(300)
            throw IllegalStateException(
                "Liquidation aggregates request failed: HTTP ${response.status.value}" +
                    if (detail.isNotBlank()) " $detail" else "",
            )
        }
        return response.body<List<MarketDataLiquidationAggregateDto>>()
            .mapNotNull { it.toBucket(symbolFallback = symbol) }
    }

    private companion object {
        const val MAX_BUCKETS = 10_000
    }
}

@Serializable
data class MarketDataLiquidationAggregateDto(
    val exchange: String = "",
    val symbol: String = "",
    val timeframe: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val longCount: Int = 0,
    val longVolume: String = "0",
    val shortCount: Int = 0,
    val shortVolume: String = "0",
) {
    fun toBucket(symbolFallback: String): LiquidationBucket? {
        if (startTime <= 0L || endTime <= startTime) return null
        val symbol = symbol.ifBlank { symbolFallback }
        val orders = buildList {
            val long = longVolume.toDoubleOrNull()?.takeIf { it > 0.0 }
            if (long != null) {
                add(
                    LiquidationOrder(
                        symbol = symbol,
                        price = 0.0,
                        quantity = long,
                        timestamp = startTime,
                        side = TradeSide.SELL,
                        orderType = AGGREGATE_ORDER_TYPE,
                    ),
                )
            }
            val short = shortVolume.toDoubleOrNull()?.takeIf { it > 0.0 }
            if (short != null) {
                add(
                    LiquidationOrder(
                        symbol = symbol,
                        price = 0.0,
                        quantity = short,
                        timestamp = startTime,
                        side = TradeSide.BUY,
                        orderType = AGGREGATE_ORDER_TYPE,
                    ),
                )
            }
        }
        if (orders.isEmpty()) return null
        return LiquidationBucket(startTime = startTime, endTime = endTime, orders = orders)
    }

    companion object {
        const val AGGREGATE_ORDER_TYPE = "AGGREGATE"
    }
}
