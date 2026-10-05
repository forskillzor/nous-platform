/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.provider.mexc.MEXC_BASE_URL
import com.aandios.nous.provider.mexc.MexcRestGate
import com.aandios.nous.provider.mexc.MexcWeights
import com.aandios.nous.provider.mexc.currentTimeMillis
import com.aandios.nous.provider.mexc.model.MexcContractDetail
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlin.concurrent.Volatile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

/**
 * SymbolInfo из `/api/v1/contract/detail` с 10-минутным кэшем: весь список
 * контрактов грузится один раз на процесс — N панелей не шлют по detail
 * каждая.
 */
class MexcSymbolInfoAdapter(
    private val client: HttpClient,
    @Suppress("unused") private val config: ProviderConfig,
    private val restGate: MexcRestGate,
) : SymbolInfoAdapter {

    companion object {
        private const val CACHE_TTL_MS = 10 * 60_000L
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var cachedAll: List<SymbolInfo>? = null

    @Volatile
    private var cachedAt = 0L

    override suspend fun getSymbolInfo(symbol: String): SymbolInfo? {
        return allSymbols().firstOrNull { it.symbol == symbol }
    }

    override suspend fun getAllSymbolsInfo(): List<SymbolInfo> = allSymbols()

    private suspend fun allSymbols(): List<SymbolInfo> {
        cachedAll?.let { cached ->
            if (currentTimeMillis() - cachedAt < CACHE_TTL_MS) return cached
        }
        val fetched = restGate.execute(key = "contractDetail", weight = MexcWeights.DETAIL) {
            val raw = client.get("$MEXC_BASE_URL/api/v1/contract/detail").body<String>()

            // data — массив всех контрактов (без ?symbol=), но допускаем и одиночный объект
            val element = json.parseToJsonElement(raw)
            val dataElement = (element as? JsonObject)?.get("data") ?: return@execute emptyList()
            val details: List<MexcContractDetail> = when (dataElement) {
                is JsonArray -> dataElement.mapNotNull {
                    runCatching { json.decodeFromString<MexcContractDetail>(it.toString()) }.getOrNull()
                }
                else -> listOf(
                    runCatching { json.decodeFromString<MexcContractDetail>(dataElement.toString()) }.getOrNull()
                ).mapNotNull { it }
            }
            details.map { it.toSymbolInfo() }
        }
        cachedAll = fetched
        cachedAt = currentTimeMillis()
        return fetched
    }
}
