/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.adapters

import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import kotlinx.coroutines.flow.Flow

interface LiquidationAdapter : MarketAdapter {
    /**
     * Subscribe to real-time liquidation orders for a symbol.
     * Binance stream: {symbol}@forceOrder
     */
    fun subscribeToLiquidations(symbol: String): Flow<LiquidationOrder>

    /**
     * Fetch historical liquidation orders via REST API.
     * @param symbol e.g. "BTCUSDT"
     * @param startTime optional start timestamp (ms)
     * @param endTime optional end timestamp (ms)
     * @param limit max records (default 100, max 1000)
     */
    suspend fun getHistoricalLiquidations(
        symbol: String,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 100
    ): List<LiquidationOrder>
}
