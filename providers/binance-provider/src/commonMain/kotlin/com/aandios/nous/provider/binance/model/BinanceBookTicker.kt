/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.model

import com.aandios.nous.api.market.model.BookTicker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BinanceBookTicker(
    @SerialName("e") val eventType: String = "bookTicker",        // "bookTicker"
    @SerialName("u") val updateId: Long = 0,           // order book updateId
    @SerialName("E") val eventTime: Long = 0,          // event time
    @SerialName("T") val transactionTime: Long = 0,    // transaction time
    @SerialName("s") val symbol: String,           // symbol
    @SerialName("b") val bestBidPrice: String,     // best bid price
    @SerialName("B") val bestBidQty: String,       // best bid quantity
    @SerialName("a") val bestAskPrice: String,     // best ask price
    @SerialName("A") val bestAskQty: String        // best ask quantity
) {
    fun toBookTicker(): BookTicker {
        return BookTicker(
            symbol = symbol,
            bestBid = bestBidPrice.toDouble(),
            bestBidQty = bestBidQty.toDouble(),
            bestAsk = bestAskPrice.toDouble(),
            bestAskQty = bestAskQty.toDouble(),
            lastPrice = (bestBidPrice.toDouble() + bestAskPrice.toDouble()) / 2, // approximate last price
            timestamp = eventTime
        )
    }
}
