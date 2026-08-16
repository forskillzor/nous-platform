/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.commands

import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType


class BuyBestBidCommand(
    private val symbol: String,
    private val bestBid: Double,
    private val quantity: Double,
    private val onResult: (CommandResult) -> Unit
) : TradingCommand {

    override suspend fun execute() {
        val orderData = OrderData(
            symbol = symbol,
            side = OrderSide.BUY,
            type = OrderType.LIMIT,  // Лимитный ордер по лучшему bid
            price = bestBid,
            quantity = quantity
        )
        onResult(CommandResult.Success(orderData))
    }

    override fun canExecute(): Boolean = bestBid > 0 && quantity > 0
    override fun getDescription(): String = "Buy @ Best Bid $bestBid"
}

class SellBestAskCommand(
    private val symbol: String,
    private val bestAsk: Double,
    private val quantity: Double,
    private val onResult: (CommandResult) -> Unit
) : TradingCommand {

    override suspend fun execute() {
        val orderData = OrderData(
            symbol = symbol,
            side = OrderSide.SELL,
            type = OrderType.LIMIT,
            price = bestAsk,
            quantity = quantity
        )
        onResult(CommandResult.Success(orderData))
    }

    override fun canExecute(): Boolean = bestAsk > 0 && quantity > 0
    override fun getDescription(): String = "Sell @ Best Ask $bestAsk"
}