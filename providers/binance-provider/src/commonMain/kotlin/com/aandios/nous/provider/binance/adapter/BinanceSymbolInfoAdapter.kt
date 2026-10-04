/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.provider.binance.BinanceRestGate
import com.aandios.nous.provider.binance.currentTimeMillis
import com.aandios.nous.provider.binance.model.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlin.concurrent.Volatile
import kotlinx.serialization.json.Json

/**
 * SymbolInfo с кэшем: exchangeInfo (весь список символов) грузится один раз
 * на процесс и живёт [CACHE_TTL_MS] — несколько ChartViewModel-панелей
 * больше не шлют по exchangeInfo каждая.
 */
class BinanceSymbolInfoAdapter(
    private val client: HttpClient,
    private val config: ProviderConfig,
    private val restGate: BinanceRestGate,
) : SymbolInfoAdapter {

    companion object {
        private const val CACHE_TTL_MS = 10 * 60_000L
    }

    private val json = Json { ignoreUnknownKeys = true }

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
        val fetched = restGate.execute(key = "exchangeInfo", weight = 1) {
            val response = client.get("${baseUrl()}/fapi/v1/exchangeInfo")
                .body<BinanceExchangeInfoResponse>()
            response.symbols.map { it.toSymbolInfo() }
        }
        cachedAll = fetched
        cachedAt = currentTimeMillis()
        return fetched
    }

    private fun baseUrl(): String = if (config.isTestnet) {
        "https://testnet.binance.vision"
    } else {
        "https://fapi.binance.com"
    }

    private fun BinanceSymbolInfo.toSymbolInfo(): SymbolInfo {
        val priceFilter = filters.filterIsInstance<BinancePriceFilter>().firstOrNull()
        val lotSizeFilter = filters.filterIsInstance<BinanceLotSizeFilter>().firstOrNull()
        val minNotionalFilter = filters.filterIsInstance<BinanceMinNotionalFilter>().firstOrNull()

        return SymbolInfo(
            symbol = symbol,
            tickSize = priceFilter?.tickSize?.toDoubleOrNull() ?: 0.01,
            stepSize = lotSizeFilter?.stepSize?.toDoubleOrNull() ?: 0.001,
            minQty = lotSizeFilter?.minQty?.toDoubleOrNull() ?: 0.001,
            minNotional = minNotionalFilter?.notional?.toDoubleOrNull() ?: 10.0,
            status = status,
            baseAsset = baseAsset,
            quoteAsset = quoteAsset,
            contractType = contractType,
            marginAsset = marginAsset,
        )
    }
}
