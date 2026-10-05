/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.provider.mexc.MEXC_BASE_URL
import com.aandios.nous.provider.mexc.MexcRestGate
import com.aandios.nous.provider.mexc.MexcStreamHub
import com.aandios.nous.provider.mexc.MexcSubscriptions
import com.aandios.nous.provider.mexc.MexcWeights
import com.aandios.nous.provider.mexc.model.MexcDeal
import com.aandios.nous.provider.mexc.model.MexcDealsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Лента сделок MEXC Futures: WS `sub.deal` → `push.deal` и REST `/deals`.
 *
 * В WS-сообщениях `data` — МАССИВ сделок, в REST — массив в `data` ответа.
 * id сделки есть только в REST (`i`), поэтому для WS синтезируем fallback
 * из time-метки и счётчика.
 */
class MexcTradesAdapter(
    private val client: HttpClient,
    @Suppress("unused") private val config: ProviderConfig,
    private val restGate: MexcRestGate,
    private val streamHub: MexcStreamHub,
) : TradesAdapter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private var idSeq = 0L

    private fun fallbackId(timestampMs: Long): Long =
        timestampMs * 1000 + (idSeq++ % 1000)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun subscribeToTrades(symbol: String): Flow<Trade> {
        val sub = MexcSubscriptions.deal(symbol)
        return streamHub.subscribe(sub)
            .map { text -> parseDeals(text).map { it.toTrade(symbol, fallbackId(it.t)) } }
            .flatMapConcat { it.asFlow() }
    }

    override suspend fun getRecentTrades(symbol: String, limit: Int): List<Trade> =
        restGate.execute(
            key = "deals:$symbol:$limit",
            weight = MexcWeights.DEALS,
        ) {
            val response = client.get("$MEXC_BASE_URL/api/v1/contract/deals/$symbol") {
                parameter("limit", limit.coerceIn(1, 100))
            }.body<MexcDealsResponse>()
            response.data.map { it.toTrade(symbol, fallbackId(it.t)) }
        }

    /** Разбирает `data` push-сообщения: массив сделок или одиночная сделка. */
    private fun parseDeals(text: String): List<MexcDeal> {
        return try {
            val el = json.parseToJsonElement(text)
            val dataEl = (el as? JsonObject)?.get("data") ?: return emptyList()
            when (dataEl) {
                is JsonArray -> dataEl.mapNotNull {
                    runCatching { json.decodeFromString<MexcDeal>(it.toString()) }.getOrNull()
                }
                else -> listOf(
                    runCatching { json.decodeFromString<MexcDeal>(dataEl.toString()) }.getOrNull()
                ).mapNotNull { it }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
