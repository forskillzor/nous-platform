/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.adapters.MarketOverviewAdapter
import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.api.market.model.MarketTickerStats
import com.aandios.nous.provider.mexc.MEXC_BASE_URL
import com.aandios.nous.provider.mexc.MexcRestGate
import com.aandios.nous.provider.mexc.MexcStreamHub
import com.aandios.nous.provider.mexc.MexcSubscriptions
import com.aandios.nous.provider.mexc.toMexcSymbol
import com.aandios.nous.provider.mexc.fromMexcSymbol
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
) : BookTickerAdapter, MarketOverviewAdapter {
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

    override suspend fun getMarketOverview(): List<MarketTickerStats> =
        restGate.execute(key = "marketOverview24h", weight = MexcWeights.TICKER) {
            val response = client.get("$MEXC_BASE_URL/api/v1/contract/ticker").body<String>()
            val root = json.parseToJsonElement(response) as? JsonObject
                ?: error("MEXC market ticker response was not an object")
            val data = root["data"] ?: return@execute emptyList()
            val rows = when (data) {
                is JsonArray -> data
                is JsonObject -> JsonArray(listOf(data))
                else -> return@execute emptyList()
            }
            rows.mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                val symbol = row.stringValue("symbol")?.let(::fromMexcSymbol) ?: return@mapNotNull null
                val lastPrice = row.doubleValue("lastPrice") ?: return@mapNotNull null
                val changeRate = row.doubleValue("riseFallRate") ?: return@mapNotNull null
                val quoteVolume = row.doubleValue("amount24") ?: return@mapNotNull null
                MarketTickerStats(symbol, lastPrice, changeRate * 100.0, quoteVolume)
            }
        }

    private fun JsonObject.stringValue(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.doubleValue(key: String): Double? =
        this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
}
