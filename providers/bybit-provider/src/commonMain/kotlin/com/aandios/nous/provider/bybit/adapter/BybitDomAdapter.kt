/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit.adapter

import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.provider.bybit.BybitStreamHub
import com.aandios.nous.provider.bybit.asArray
import com.aandios.nous.provider.bybit.asObject
import com.aandios.nous.provider.bybit.bybitBookDepth
import com.aandios.nous.provider.bybit.bybitBookWindow
import com.aandios.nous.provider.bybit.string
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class BybitDomAdapter internal constructor(
    private val streams: BybitStreamHub,
) : DomAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun subscribeToBookWindow(symbol: String, depth: Int): Flow<BookWindowLevels> {
        val exchangeDepth = bybitBookDepth(depth)
        val topic = "orderbook.$exchangeDepth.$symbol"
        return flow {
            val bids = mutableMapOf<Double, Double>()
            val asks = mutableMapOf<Double, Double>()
            streams.subscribe(topic).collect { text ->
                val root = json.parseToJsonElement(text).asObject() ?: return@collect
                if (root.string("topic") != topic) return@collect
                val data = root["data"]?.asObject() ?: return@collect
                if (root.string("type") == "snapshot") {
                    bids.clear()
                    asks.clear()
                } else if (root.string("type") != "delta") {
                    return@collect
                }
                applyLevels(data["b"]?.asArray(), bids)
                applyLevels(data["a"]?.asArray(), asks)
                emit(bybitBookWindow(bids, asks, depth.coerceAtLeast(1)))
            }
        }
    }

    private fun applyLevels(
        levels: kotlinx.serialization.json.JsonArray?,
        bookSide: MutableMap<Double, Double>,
    ) {
        levels.orEmpty().forEach { row ->
            val values = row.asArray() ?: return@forEach
            val price = values.getOrNull(0)?.toString()?.removeSurrounding("\"")?.toDoubleOrNull()
                ?: return@forEach
            val quantity = values.getOrNull(1)?.toString()?.removeSurrounding("\"")?.toDoubleOrNull()
                ?: return@forEach
            if (quantity == 0.0) bookSide.remove(price) else bookSide[price] = quantity
        }
    }
}
