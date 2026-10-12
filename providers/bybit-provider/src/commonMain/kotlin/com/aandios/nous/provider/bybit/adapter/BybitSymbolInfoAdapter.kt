/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit.adapter

import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.provider.bybit.BybitRestClient
import com.aandios.nous.provider.bybit.asArray
import com.aandios.nous.provider.bybit.asObject
import com.aandios.nous.provider.bybit.string
import kotlinx.serialization.json.JsonObject
import kotlin.concurrent.Volatile

class BybitSymbolInfoAdapter internal constructor(
    private val rest: BybitRestClient,
) : SymbolInfoAdapter {
    @Volatile
    private var cachedSymbols: List<SymbolInfo>? = null
    @Volatile
    private var cachedAt = 0L

    override suspend fun getSymbolInfo(symbol: String): SymbolInfo? =
        getAllSymbolsInfo().firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }

    override suspend fun getAllSymbolsInfo(): List<SymbolInfo> {
        val now = com.aandios.nous.provider.bybit.bybitCurrentTimeMillis()
        cachedSymbols?.takeIf { now - cachedAt < CACHE_TTL_MS }?.let { return it }
        val symbols = mutableListOf<SymbolInfo>()
        var cursor: String? = null
        do {
            val params = mutableMapOf("category" to "linear", "limit" to "1000")
            cursor?.let { params["cursor"] = it }
            val result = rest.get("/v5/market/instruments-info", params)
            result["list"]?.asArray().orEmpty()
                .mapNotNull { it.asObject()?.toSymbolInfo() }
                .filterTo(symbols) {
                    it.quoteAsset == "USDT" &&
                        it.contractType == PLATFORM_PERPETUAL &&
                        it.status == "TRADING"
                }
            cursor = result.string("nextPageCursor").orEmpty().ifEmpty { null }
        } while (cursor != null)
        return symbols.also {
            cachedSymbols = it
            cachedAt = com.aandios.nous.provider.bybit.bybitCurrentTimeMillis()
        }
    }

    private fun JsonObject.toSymbolInfo(): SymbolInfo? {
        val symbol = string("symbol") ?: return null
        val quote = string("quoteCoin") ?: return null
        val settle = string("settleCoin") ?: return null
        val base = string("baseCoin") ?: return null
        val contract = string("contractType") ?: return null
        val status = string("status") ?: return null
        val priceFilter = this["priceFilter"]?.asObject() ?: return null
        val lotSizeFilter = this["lotSizeFilter"]?.asObject() ?: return null
        val tickSize = priceFilter.string("tickSize")?.toDoubleOrNull() ?: return null
        val stepSize = lotSizeFilter.string("qtyStep")?.toDoubleOrNull() ?: return null
        val minQty = lotSizeFilter.string("minOrderQty")?.toDoubleOrNull() ?: return null
        return SymbolInfo(
            symbol = symbol,
            tickSize = tickSize,
            stepSize = stepSize,
            minQty = minQty,
            minNotional = lotSizeFilter.string("minNotionalValue")?.toDoubleOrNull() ?: 0.0,
            status = status.uppercase(),
            baseAsset = base,
            quoteAsset = quote,
            contractType = if (contract == LINEAR_PERPETUAL) PLATFORM_PERPETUAL else contract,
            marginAsset = settle,
        )
    }

    private companion object {
        const val CACHE_TTL_MS = 10 * 60_000L
        const val LINEAR_PERPETUAL = "LinearPerpetual"
        const val PLATFORM_PERPETUAL = "PERPETUAL"
    }
}
