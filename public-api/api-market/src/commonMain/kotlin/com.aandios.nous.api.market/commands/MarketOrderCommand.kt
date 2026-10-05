/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.commands

import com.aandios.nous.api.market.adapters.TradingAdapter
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.OrderRequest

/**
 * Общий исполнитель рыночных/лимитных ордеров: если передан
 * [tradingAdapter] — ордер реально уходит на биржу; без адаптера —
 * ошибка «Trading adapter not available».
 */
private suspend fun executeOrder(
    tradingAdapter: TradingAdapter?,
    symbol: String,
    side: OrderSide,
    orderType: OrderType,
    price: Double?,
    quantity: Double,
    reduceOnly: Boolean,
    leverage: Int? = null,
    onResult: (CommandResult) -> Unit,
): CommandResult {
    val orderData = OrderData(
        symbol = symbol,
        side = side,
        type = orderType,
        price = price,
        quantity = quantity,
    )
    val adapter = tradingAdapter
    if (adapter == null) {
        return CommandResult.Error("Trading adapter not available")
    }
    return try {
        val response = adapter.placeOrder(
            OrderRequest(
                symbol = symbol,
                side = side,
                orderType = orderType,
                quantity = quantity,
                price = price ?: 0.0,
                reduceOnly = reduceOnly,
                leverage = leverage,
            )
        )
        if (response.success) CommandResult.Success(orderData)
        else CommandResult.Error(response.message ?: "Order failed")
    } catch (e: Exception) {
        CommandResult.Error(e.message ?: "Order failed")
    }
}

class BuyMarketCommand(
    private val symbol: String,
    private val quantity: Double,
    private val reduceOnly: Boolean = false,
    private val leverage: Int? = null,
    private val tradingAdapter: TradingAdapter? = null,
    private val onResult: (CommandResult) -> Unit,
) : TradingCommand {

    override suspend fun execute() {
        onResult(
            executeOrder(tradingAdapter, symbol, OrderSide.BUY, OrderType.MARKET, null, quantity, reduceOnly, leverage, onResult)
        )
    }

    override fun canExecute(): Boolean = quantity > 0
    override fun getDescription(): String = "Buy Market $quantity $symbol"
}

class SellMarketCommand(
    private val symbol: String,
    private val quantity: Double,
    private val reduceOnly: Boolean = false,
    private val leverage: Int? = null,
    private val tradingAdapter: TradingAdapter? = null,
    private val onResult: (CommandResult) -> Unit,
) : TradingCommand {

    override suspend fun execute() {
        onResult(
            executeOrder(tradingAdapter, symbol, OrderSide.SELL, OrderType.MARKET, null, quantity, reduceOnly, leverage, onResult)
        )
    }

    override fun canExecute(): Boolean = quantity > 0
    override fun getDescription(): String = "Sell Market $quantity $symbol"
}
