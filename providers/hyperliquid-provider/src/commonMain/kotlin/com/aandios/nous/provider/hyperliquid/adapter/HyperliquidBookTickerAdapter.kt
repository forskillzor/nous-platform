/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid.adapter

import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.adapters.MarketOverviewAdapter
import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.api.market.model.MarketTickerStats
import com.aandios.nous.provider.hyperliquid.HyperliquidInfoApi
import com.aandios.nous.provider.hyperliquid.HyperliquidMarketData
import com.aandios.nous.provider.hyperliquid.HyperliquidStreamApi
import com.aandios.nous.provider.hyperliquid.asObject
import com.aandios.nous.provider.hyperliquid.hyperliquidBbo
import com.aandios.nous.provider.hyperliquid.hyperliquidBookTickerFromL2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HyperliquidBookTickerAdapter internal constructor(
    private val info: HyperliquidInfoApi,
    private val streams: HyperliquidStreamApi,
    private val marketData: HyperliquidMarketData,
) : BookTickerAdapter, MarketOverviewAdapter {

    override fun subscribeToBookTicker(symbol: String): Flow<BookTicker> {
        val key = "bbo|$symbol"
        val subscription = buildJsonObject {
            put("type", "bbo")
            put("coin", symbol)
        }
        return flow {
            streams.subscribe(key, subscription).collect { root ->
                val data = root["data"]?.asObject() ?: return@collect
                hyperliquidBbo(data)?.let { emit(it) }
            }
        }
    }

    override suspend fun getBookTickerRest(symbol: String): BookTicker? {
        val body = buildJsonObject {
            put("type", "l2Book")
            put("coin", symbol)
        }
        return info.info(body).asObject()?.let(::hyperliquidBookTickerFromL2)
    }

    override suspend fun getMarketOverview(): List<MarketTickerStats> = marketData.marketOverview()
}
