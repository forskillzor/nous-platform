/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.commands

import com.aandios.nous.api.market.adapters.TradingAdapter
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType

/**
 * Лимитные ордера по лучшему bid/ask с реальным исполнением через адаптер.
 */
class BuyBestBidCommand(
    private val symbol: String,
    private val bestBid: Double,
    private val quantity: Double,
    private val reduceOnly: Boolean = false,
    private val leverage: Int? = null,
    private val tradingAdapter: TradingAdapter? = null,
    private val onResult: (CommandResult) -> Unit,
) : TradingCommand {

    override suspend fun execute() {
        val orderData = OrderData(
            symbol = symbol,
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = bestBid,
            quantity = quantity,
        )
        val adapter = tradingAdapter
        if (adapter == null) {
            onResult(CommandResult.Error("Trading adapter not available"))
            return
        }
        val response = try {
            adapter.placeOrder(
                com.aandios.nous.api.market.model.trading.OrderRequest(
                    symbol = symbol,
                    side = OrderSide.BUY,
                    orderType = OrderType.LIMIT,
                    quantity = quantity,
                    price = bestBid,
                    reduceOnly = reduceOnly,
                    leverage = leverage,
                )
            )
        } catch (e: Exception) {
            onResult(CommandResult.Error(e.message ?: "Order failed")); return
        }
        onResult(
            if (response.success) CommandResult.Success(orderData)
            else CommandResult.Error(response.message ?: "Order failed")
        )
    }

    override fun canExecute(): Boolean = bestBid > 0 && quantity > 0
    override fun getDescription(): String = "Buy @ Best Bid $bestBid"
}

class SellBestAskCommand(
    private val symbol: String,
    private val bestAsk: Double,
    private val quantity: Double,
    private val reduceOnly: Boolean = false,
    private val leverage: Int? = null,
    private val tradingAdapter: TradingAdapter? = null,
    private val onResult: (CommandResult) -> Unit,
) : TradingCommand {

    override suspend fun execute() {
        val orderData = OrderData(
            symbol = symbol,
            side = OrderSide.SELL,
            type = OrderType.LIMIT,
            price = bestAsk,
            quantity = quantity,
        )
        val adapter = tradingAdapter
        if (adapter == null) {
            onResult(CommandResult.Error("Trading adapter not available"))
            return
        }
        val response = try {
            adapter.placeOrder(
                com.aandios.nous.api.market.model.trading.OrderRequest(
                    symbol = symbol,
                    side = OrderSide.SELL,
                    orderType = OrderType.LIMIT,
                    quantity = quantity,
                    price = bestAsk,
                    reduceOnly = reduceOnly,
                    leverage = leverage,
                )
            )
        } catch (e: Exception) {
            onResult(CommandResult.Error(e.message ?: "Order failed")); return
        }
        onResult(
            if (response.success) CommandResult.Success(orderData)
            else CommandResult.Error(response.message ?: "Order failed")
        )
    }

    override fun canExecute(): Boolean = bestAsk > 0 && quantity > 0
    override fun getDescription(): String = "Sell @ Best Ask $bestAsk"
}
