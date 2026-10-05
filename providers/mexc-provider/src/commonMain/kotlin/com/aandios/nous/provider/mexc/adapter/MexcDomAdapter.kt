/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.provider.mexc.MexcStreamHub
import com.aandios.nous.provider.mexc.toMexcSymbol
import com.aandios.nous.provider.mexc.MexcSubscriptions
import com.aandios.nous.provider.mexc.model.MexcDepthPush
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * DOM MEXC Futures: WS `sub.depth.full` — самодостаточный срез топ-20
 * уровней стакана (без инкрементальной синхронизации). Обрезку до нужной
 * глубины панели делаем на стороне адаптера.
 */
class MexcDomAdapter(
    @Suppress("unused") private val client: HttpClient,
    @Suppress("unused") private val config: ProviderConfig,
    private val streamHub: MexcStreamHub,
) : DomAdapter {

    companion object {
        /** MEXC sub.depth.full поддерживает limit 5/10/20 — берём максимум. */
        private const val SUBSCRIBED_LEVELS = 20
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun subscribeToBookWindow(symbol: String, depth: Int): Flow<BookWindowLevels> {
        val sub = MexcSubscriptions.depthFull(toMexcSymbol(symbol), SUBSCRIBED_LEVELS)
        return streamHub.subscribe(sub).map { text ->
            val levels = json.decodeFromString<MexcDepthPush>(text)
                .data?.toBookWindowLevels() ?: BookWindowLevels(emptyList(), emptyList())
            BookWindowLevels(
                bids = levels.bids.take(depth.coerceAtLeast(1)),
                asks = levels.asks.take(depth.coerceAtLeast(1)),
            )
        }
    }
}
