/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid.adapter

import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.provider.hyperliquid.HyperliquidStreamApi
import com.aandios.nous.provider.hyperliquid.asObject
import com.aandios.nous.provider.hyperliquid.hyperliquidBookWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HyperliquidDomAdapter internal constructor(
    private val streams: HyperliquidStreamApi,
) : DomAdapter {

    override suspend fun subscribeToBookWindow(symbol: String, depth: Int): Flow<BookWindowLevels> {
        val requested = depth.coerceIn(1, HYPERLIQUID_BOOK_DEPTH)
        val key = "l2Book|$symbol"
        val subscription = buildJsonObject {
            put("type", "l2Book")
            put("coin", symbol)
        }
        return flow {
            streams.subscribe(key, subscription).collect { root ->
                val data = root["data"]?.asObject() ?: return@collect
                emit(hyperliquidBookWindow(data, requested))
            }
        }
    }

    private companion object {
        /** Hyperliquid exposes at most 20 L2 levels per side. */
        const val HYPERLIQUID_BOOK_DEPTH = 20
    }
}
