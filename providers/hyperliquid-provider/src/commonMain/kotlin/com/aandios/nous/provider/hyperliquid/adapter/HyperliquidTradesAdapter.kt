/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid.adapter

import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.provider.hyperliquid.HyperliquidInfoApi
import com.aandios.nous.provider.hyperliquid.HyperliquidStreamApi
import com.aandios.nous.provider.hyperliquid.asArray
import com.aandios.nous.provider.hyperliquid.asObject
import com.aandios.nous.provider.hyperliquid.hyperliquidTrade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HyperliquidTradesAdapter internal constructor(
    private val info: HyperliquidInfoApi,
    private val streams: HyperliquidStreamApi,
) : TradesAdapter {

    override fun subscribeToTrades(symbol: String): Flow<Trade> {
        val key = "trades|$symbol"
        val subscription = buildJsonObject {
            put("type", "trades")
            put("coin", symbol)
        }
        return flow {
            streams.subscribe(key, subscription).collect { root ->
                val data = root["data"]
                val rows = data?.asArray() ?: listOfNotNull(data?.asObject())
                rows.forEach { element ->
                    element.asObject()?.let(::hyperliquidTrade)?.let { emit(it) }
                }
            }
        }
    }

    override suspend fun getRecentTrades(symbol: String, limit: Int): List<Trade> {
        val body = buildJsonObject {
            put("type", "recentTrades")
            put("coin", symbol)
        }
        return info.info(body).asArray().orEmpty()
            .mapNotNull { it.asObject()?.let(::hyperliquidTrade) }
            .take(limit.coerceIn(1, 2_000))
    }
}
