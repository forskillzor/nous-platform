/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.provider.binance.BinanceStreamHub
import com.aandios.nous.provider.binance.model.BinanceLiquidationEvent
import com.aandios.nous.provider.binance.model.toLiquidationOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json

class BinanceLiquidationAdapter(
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
        // Публичный Binance allForceOrders закрыт; история приходит из
        // market-data-server, live — из @forceOrder выше.
        return emptyList()
    }
}
