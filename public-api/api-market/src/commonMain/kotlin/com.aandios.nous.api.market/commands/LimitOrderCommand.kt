/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.commands

import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType


class BuyLimitCommand(
    private val symbol: String,
    private val price: Double,
    private val quantity: Double,
    private val onResult: (CommandResult) -> Unit
) : TradingCommand {

    override suspend fun execute() {
        val orderData = OrderData(
            symbol = symbol,
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = price,
            quantity = quantity
        )
        onResult(CommandResult.Success(orderData))
    }

    override fun canExecute(): Boolean = price > 0 && quantity > 0
    override fun getDescription(): String = "Buy Limit $quantity @ $price"
}

class SellLimitCommand(
    private val symbol: String,
    private val price: Double,
    private val quantity: Double,
    private val onResult: (CommandResult) -> Unit
) : TradingCommand {

    override suspend fun execute() {
        val orderData = OrderData(
            symbol = symbol,
            side = OrderSide.SELL,
            type = OrderType.LIMIT,
            price = price,
            quantity = quantity
        )
        onResult(CommandResult.Success(orderData))
    }

    override fun canExecute(): Boolean = price > 0 && quantity > 0
    override fun getDescription(): String = "Sell Limit $quantity @ $price"
}