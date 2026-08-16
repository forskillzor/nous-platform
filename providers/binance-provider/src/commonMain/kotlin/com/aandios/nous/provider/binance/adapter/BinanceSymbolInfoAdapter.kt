/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.provider.binance.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.Json

class BinanceSymbolInfoAdapter(
    private val client: HttpClient,
    private val config: ProviderConfig,
) : SymbolInfoAdapter {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getSymbolInfo(symbol: String): SymbolInfo? {
        return try {
            val response = client.get("${baseUrl()}/fapi/v1/exchangeInfo") {
                url {
                    parameters.append("symbol", symbol)
                }
            }.body<BinanceExchangeInfoResponse>()

            response.symbols.firstOrNull { it.symbol == symbol }?.toSymbolInfo()
        } catch (e: Exception) {
            println("❌ Failed to fetch symbol info for $symbol: ${e.message}")
            null
        }
    }

    override suspend fun getAllSymbolsInfo(): List<SymbolInfo> {
        return try {
            val response = client.get("${baseUrl()}/fapi/v1/exchangeInfo")
                .body<BinanceExchangeInfoResponse>()
            response.symbols.map { it.toSymbolInfo() }
        } catch (e: Exception) {
            println("❌ Failed to fetch all symbols info: ${e.message}")
            emptyList()
        }
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
        )
    }
}