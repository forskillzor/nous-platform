/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.adapters

import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.trading.FeeRates
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.OrderResponse
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeFill
import kotlinx.coroutines.flow.Flow

/**
 * Торговый адаптер провайдера (ордера/позиции/счёт).
 *
 * Методы ниже [placeOrder] имеют дефолтные реализации-заглушки, чтобы
 * провайдеры могли реализовывать их постепенно.
 */
interface TradingAdapter : MarketAdapter {

    /** Разместить ордер. */
    suspend fun placeOrder(request: OrderRequest): OrderResponse

    /** Отменить ордер по id. */
    suspend fun cancelOrder(orderId: String): Boolean

    /** Отменить все ордера (опционально — только по символу). */
    suspend fun cancelAllOrders(symbol: String? = null): Boolean = false

    /** Закрыть позицию рыночным reduce-only ордером. */
    suspend fun closePosition(symbol: String, positionId: Long? = null, quantity: Double? = null): OrderResponse? = null

    /** Балансы счёта. */
    suspend fun getBalances(): List<Balance>

    /** Открытые позиции. */
    suspend fun getPositions(): List<Position>

    /** Открытые ордера (null symbol — все). */
    suspend fun getOpenOrders(symbol: String? = null): List<Order> = emptyList()

    /** История сделок (fills). */
    suspend fun getTradeHistory(symbol: String? = null, limit: Int = 100): List<TradeFill> = emptyList()

    /** Установить плечо для позиции/символа. */
    suspend fun setLeverage(symbol: String, leverage: Int, positionId: Long? = null): Boolean = false

    /** Текущее плечо по символу. */
    suspend fun getLeverage(symbol: String): Int? = null

    /** Режим позиций: 1 — hedge, 2 — one-way. */
    suspend fun getPositionMode(): Int? = null

    /** Переключить режим позиций (только без открытых ордеров/позиций). */
    suspend fun setPositionMode(mode: Int): Boolean = false

    /** Изменить маржу позиции (ADD/SUB). */
    suspend fun adjustMargin(positionId: Long, amount: Double, add: Boolean): Boolean = false

    /** Живые обновления позиций (null — провайдер не поддерживает). */
    fun subscribeToPositions(): Flow<Position>? = null

    /** Живые обновления ордеров. */
    fun subscribeToOrders(): Flow<Order>? = null

    /** Живые обновления балансов. */
    fun subscribeToBalances(): Flow<Balance>? = null

    /**
     * Пользовательские уведомления адаптера (отказ ордера, ошибки движка),
     * если провайдер умеет их публиковать. UI показывает их в snackbar.
     */
    fun notices(): Flow<String>? = null

    /**
     * Ставки комиссий по символу (maker/taker) из данных активной биржи.
     * Провайдеры реализуют своим источником (MEXC — tiered_fee_rate/v2,
     * Binance и прочие — их exchange/account API). null — недоступно
     * (нет ключей/эндпоинта): paper-движок использует 0%.
     */
    suspend fun getFeeRates(symbol: String): FeeRates? = null
}
