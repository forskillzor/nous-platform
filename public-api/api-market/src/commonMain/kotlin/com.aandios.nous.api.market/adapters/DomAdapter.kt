/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.adapters

import com.aandios.nous.api.market.model.orderbook.DepthSnapshot
import com.aandios.nous.api.market.model.orderbook.DepthUpdate
import kotlinx.coroutines.flow.Flow

interface DomAdapter: MarketAdapter {
    /**
     * Dom Snapshot
     */
    suspend fun getOrderBookSnapshot(symbol: String, depth: Int = 20): DepthSnapshot

    /**
     * Subscribe to order book updates
     */
    suspend fun subscribeToDepthUpdates(symbol: String, depth: Int): Flow<DepthUpdate>
}