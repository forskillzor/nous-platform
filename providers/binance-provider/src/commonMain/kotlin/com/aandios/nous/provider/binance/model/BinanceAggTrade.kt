/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.model

import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BinanceAggTrade(
    @SerialName("e") val eventType: String = "", // "aggTrade"
    @SerialName("E") val eventTime: Long = 0,    // Event time
    @SerialName("s") val symbol: String = "",    // Symbol
    @SerialName("a") val aggregatedTradeId: Long = 0, // Aggregate trade ID
    @SerialName("p") val price: String = "",     // Price
    @SerialName("q") val quantity: String = "",  // Quantity
    @SerialName("f") val firstTradeId: Long = 0, // First trade ID
    @SerialName("l") val lastTradeId: Long = 0,  // Last trade ID
    @SerialName("T") val tradeTime: Long = 0,    // Trade time
    @SerialName("m") val isBuyerMaker: Boolean = false, // Is the buyer the market maker?
    @SerialName("M") val ignore: Boolean = false // Ignore
) {
    fun toTrade(): Trade {
        return Trade(
            id = this.aggregatedTradeId,
            symbol = this.symbol,
            price = this.price.toDouble(),
            quantity = this.quantity.toDouble(),
            timestamp = this.tradeTime,
            isBuyerMaker = this.isBuyerMaker,
            side = if (this.isBuyerMaker) TradeSide.SELL else TradeSide.BUY
        )
    }
}
