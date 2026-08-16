/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.adapters

import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.OrderResponse
import com.aandios.nous.api.market.model.trading.Position


interface TradingAdapter: MarketAdapter {
    /**
     * Размещение ордера
     */
    suspend fun placeOrder(request: OrderRequest): OrderResponse

    /**
     * Отмена ордера
     */
    suspend fun cancelOrder(orderId: String): Boolean

    /**
     * Получение баланса
     */
    suspend fun getBalances(): List<Balance>

    /**
     * Получение открытых позиций
     */
    suspend fun getPositions(): List<Position>
}