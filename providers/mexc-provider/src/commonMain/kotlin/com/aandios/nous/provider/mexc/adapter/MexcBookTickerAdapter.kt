/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.provider.mexc.MEXC_BASE_URL
import com.aandios.nous.provider.mexc.MexcRestGate
import com.aandios.nous.provider.mexc.MexcStreamHub
import com.aandios.nous.provider.mexc.MexcSubscriptions
import com.aandios.nous.provider.mexc.toMexcSymbol
import com.aandios.nous.provider.mexc.MexcWeights
import com.aandios.nous.provider.mexc.model.MexcTickerPush
import com.aandios.nous.provider.mexc.model.MexcTickerResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json

/**
 * Лучшие цены MEXC Futures: WS `sub.ticker` → `push.ticker` и REST
 * `/api/v1/contract/ticker?symbol=`. Объёмов по лучшим ценам MEXC не
 * отдаёт — поля bestBidQty/bestAskQty = 0.
 */
class MexcBookTickerAdapter(
    private val client: HttpClient,
    @Suppress("unused") private val config: ProviderConfig,
    private val restGate: MexcRestGate,
    private val streamHub: MexcStreamHub,
) : BookTickerAdapter {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun subscribeToBookTicker(symbol: String): Flow<BookTicker> {
        val sub = MexcSubscriptions.ticker(toMexcSymbol(symbol))
        return streamHub.subscribe(sub).map { text ->
            json.decodeFromString<MexcTickerPush>(text).data?.toBookTicker()
        }.mapNotNull { it }
    }

    override suspend fun getBookTickerRest(symbol: String): BookTicker? {
        return try {
            restGate.execute(key = "ticker:$symbol", weight = MexcWeights.TICKER) {
                val response = client.get("$MEXC_BASE_URL/api/v1/contract/ticker") {
                    parameter("symbol", toMexcSymbol(symbol))
                }.body<MexcTickerResponse>()

                response.data?.toBookTicker()
            }
        } catch (e: Exception) {
            println("❌ Mexc: failed to get ticker via REST: ${e.message}")
            null
        }
    }
}
