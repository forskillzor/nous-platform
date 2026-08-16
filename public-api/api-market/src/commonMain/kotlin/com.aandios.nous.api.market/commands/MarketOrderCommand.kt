/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.commands

import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType


class BuyMarketCommand(
    private val symbol: String,
    private val quantity: Double,
    private val onResult: (CommandResult) -> Unit
) : TradingCommand {

    override suspend fun execute() {
        val orderData = OrderData(
            symbol = symbol,
            side = OrderSide.BUY,
            type = OrderType.MARKET,
            quantity = quantity
        )
        // Здесь будет логика отправки на биржу
        onResult(CommandResult.Success(orderData))
    }

    override fun canExecute(): Boolean = quantity > 0

    override fun getDescription(): String = "Buy Market $quantity $symbol"
}

class SellMarketCommand(
    private val symbol: String,
    private val quantity: Double,
    private val onResult: (CommandResult) -> Unit
) : TradingCommand {

    override suspend fun execute() {
        val orderData = OrderData(
            symbol = symbol,
            side = OrderSide.SELL,
            type = OrderType.MARKET,
            quantity = quantity
        )
        onResult(CommandResult.Success(orderData))
    }

    override fun canExecute(): Boolean = quantity > 0
    override fun getDescription(): String = "Sell Market $quantity $symbol"
}