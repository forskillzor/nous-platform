/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market

import com.aandios.nous.api.market.adapters.*

interface Provider {
    val providerId: String
    val providerName: String
    val version: String
    val config: ProviderConfig
    val networkManager: NetworkManager

    /**
     * Получить адаптер по enum (для случаев, когда тип известен только в рантайме)
     */
    val trades: TradesAdapter?
    val dom: DomAdapter?
    val bookTicker: BookTickerAdapter?
    val chart: ChartAdapter?
    val trading: TradingAdapter?
    val symbolInfo: SymbolInfoAdapter?
    val liquidation: LiquidationAdapter?

    suspend fun start() {}

    suspend fun stop() {}
}