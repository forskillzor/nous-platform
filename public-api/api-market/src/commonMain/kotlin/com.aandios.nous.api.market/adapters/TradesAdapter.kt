/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.adapters

import com.aandios.nous.api.market.model.trades.Trade
import kotlinx.coroutines.flow.Flow

interface TradesAdapter: MarketAdapter {
    /**
     * Realtime Times and Sales
     */
    fun subscribeToTrades(symbol: String): Flow<Trade>

    /**
     * History trades (optional)
     */
    suspend fun getRecentTrades(symbol: String, limit: Int): List<Trade>
}