/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.TradingAdapter
import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.OrderResponse
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.provider.mexc.currentTimeMillis
import io.ktor.client.HttpClient

/**
 * Торговля MEXC — заглушка (как у Binance): приватный API требует ключей
 * и подписи, за пределами текущего покрытия.
 */
class MexcTradingAdapter(
    @Suppress("unused") client: HttpClient,
    @Suppress("unused") val config: ProviderConfig,
) : TradingAdapter {
    override suspend fun placeOrder(request: OrderRequest): OrderResponse {
        println("📝 MexcTradingAdapter.placeOrder: $request")
        // fixme: Реальная реализация должна вызывать MEXC API с подписью
        return OrderResponse(
            orderId = "TEST-${currentTimeMillis()}",
            price = request.price
        )
    }

    override suspend fun cancelOrder(orderId: String): Boolean {
        println("📝 MexcTradingAdapter.cancelOrder: $orderId")
        return true
    }

    override suspend fun getBalances(): List<Balance> {
        println("📝 MexcTradingAdapter.getBalances")
        return emptyList()
    }

    override suspend fun getPositions(): List<Position> {
        println("📝 MexcTradingAdapter.getPositions")
        return emptyList()
    }
}
