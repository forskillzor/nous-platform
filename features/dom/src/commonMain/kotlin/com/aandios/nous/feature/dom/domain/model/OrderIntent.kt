/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain.model

import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType

/**
 * Намерение разместить ордер, сгенерированное панелью размещения ордеров.
 * Обрабатывается ViewModel для создания соответствующей TradingCommand.
 */
sealed class OrderIntent {
    /** Рыночный ордер на покупку */
    data class MarketBuy(
        val symbol: String,
        val quantity: Double
    ) : OrderIntent()

    /** Рыночный ордер на продажу */
    data class MarketSell(
        val symbol: String,
        val quantity: Double
    ) : OrderIntent()

    /** Лимитный ордер на покупку по указанной цене */
    data class LimitBuy(
        val symbol: String,
        val price: Double,
        val quantity: Double
    ) : OrderIntent()

    /** Лимитный ордер на продажу по указанной цене */
    data class LimitSell(
        val symbol: String,
        val price: Double,
        val quantity: Double
    ) : OrderIntent()

    /** Лимитный ордер на покупку по лучшему биду */
    data class BestBidBuy(
        val symbol: String,
        val bestBidPrice: Double,
        val quantity: Double
    ) : OrderIntent()

    /** Лимитный ордер на продажу по лучшему аску */
    data class BestAskSell(
        val symbol: String,
        val bestAskPrice: Double,
        val quantity: Double
    ) : OrderIntent()

    /** Включить/выключить торговлю (аналог TradeOffCommand) */
    object ToggleTrading : OrderIntent()

    /** Преобразует намерение в данные ордера (OrderData) */
    fun toOrderData(): com.aandios.nous.api.market.commands.OrderData? = when (this) {
        is MarketBuy -> com.aandios.nous.api.market.commands.OrderData(
            symbol = symbol,
            side = OrderSide.BUY,
            type = OrderType.MARKET,
            quantity = quantity
        )
        is MarketSell -> com.aandios.nous.api.market.commands.OrderData(
            symbol = symbol,
            side = OrderSide.SELL,
            type = OrderType.MARKET,
            quantity = quantity
        )
        is LimitBuy -> com.aandios.nous.api.market.commands.OrderData(
            symbol = symbol,
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = price,
            quantity = quantity
        )
        is LimitSell -> com.aandios.nous.api.market.commands.OrderData(
            symbol = symbol,
            side = OrderSide.SELL,
            type = OrderType.LIMIT,
            price = price,
            quantity = quantity
        )
        is BestBidBuy -> com.aandios.nous.api.market.commands.OrderData(
            symbol = symbol,
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = bestBidPrice,
            quantity = quantity
        )
        is BestAskSell -> com.aandios.nous.api.market.commands.OrderData(
            symbol = symbol,
            side = OrderSide.SELL,
            type = OrderType.LIMIT,
            price = bestAskPrice,
            quantity = quantity
        )
        ToggleTrading -> null
    }
}