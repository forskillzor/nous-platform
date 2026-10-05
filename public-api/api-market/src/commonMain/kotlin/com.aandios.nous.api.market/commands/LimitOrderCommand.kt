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
 * Лимитные ордера (и их варианты POST_ONLY/IOC/FOK) с реальным исполнением
 * через [tradingAdapter], если он задан.
 */
private suspend fun executeLimitOrder(
    tradingAdapter: TradingAdapter?,
    symbol: String,
    side: OrderSide,
    orderType: OrderType,
    price: Double,
    quantity: Double,
    reduceOnly: Boolean,
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
                price = price,
                reduceOnly = reduceOnly,
            )
        )
        if (response.success) CommandResult.Success(orderData)
        else CommandResult.Error(response.message ?: "Order failed")
    } catch (e: Exception) {
        CommandResult.Error(e.message ?: "Order failed")
    }
}

class BuyLimitCommand(
    private val symbol: String,
    private val price: Double,
    private val quantity: Double,
    private val orderType: OrderType = OrderType.LIMIT,
    private val reduceOnly: Boolean = false,
    private val tradingAdapter: TradingAdapter? = null,
    private val onResult: (CommandResult) -> Unit,
) : TradingCommand {

    override suspend fun execute() {
        onResult(
            executeLimitOrder(tradingAdapter, symbol, OrderSide.BUY, orderType, price, quantity, reduceOnly, onResult)
        )
    }

    override fun canExecute(): Boolean = price > 0 && quantity > 0
    override fun getDescription(): String = "Buy Limit $quantity @ $price"
}

class SellLimitCommand(
    private val symbol: String,
    private val price: Double,
    private val quantity: Double,
    private val orderType: OrderType = OrderType.LIMIT,
    private val reduceOnly: Boolean = false,
    private val tradingAdapter: TradingAdapter? = null,
    private val onResult: (CommandResult) -> Unit,
) : TradingCommand {

    override suspend fun execute() {
        onResult(
            executeLimitOrder(tradingAdapter, symbol, OrderSide.SELL, orderType, price, quantity, reduceOnly, onResult)
        )
    }

    override fun canExecute(): Boolean = price > 0 && quantity > 0
    override fun getDescription(): String = "Sell Limit $quantity @ $price"
}
