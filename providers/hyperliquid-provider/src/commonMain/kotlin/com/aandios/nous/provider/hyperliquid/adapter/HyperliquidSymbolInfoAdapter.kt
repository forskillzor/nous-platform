/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid.adapter

import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.provider.hyperliquid.HyperliquidMarketData

class HyperliquidSymbolInfoAdapter internal constructor(
    private val marketData: HyperliquidMarketData,
) : SymbolInfoAdapter {
    override suspend fun getSymbolInfo(symbol: String): SymbolInfo? = marketData.symbolInfo(symbol)

    override suspend fun getAllSymbolsInfo(): List<SymbolInfo> = marketData.symbols()
}
