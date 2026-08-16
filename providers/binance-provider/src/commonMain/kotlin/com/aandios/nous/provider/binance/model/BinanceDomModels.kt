/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.model

import com.aandios.nous.provider.binance.currentTimeMillis
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BinanceDepthUpdate(
    @SerialName("e") val eventType: String = "",
    @SerialName("E") val eventTime: Long = 0,
    @SerialName("T") val transactionTime: Long = 0,
    @SerialName("s") val symbol: String = "",
    @SerialName("U") val firstUpdateId: Long = 0,
    @SerialName("u") val lastUpdateId: Long = 0,
    @SerialName("pu") val prevLastUpdateId: Long = 0,
    @SerialName("b") val bids: List<List<String>> = emptyList(),
    @SerialName("a") val asks: List<List<String>> = emptyList()
)

@Serializable
data class OrderBookLevel(
    val price: String,
    val quantity: String,
    val total: String = "0" // Будем вычислять
)

@Serializable
data class OrderBook(
    val symbol: String,
    val bids: List<OrderBookLevel> = emptyList(),
    val asks: List<OrderBookLevel> = emptyList(),
    val lastUpdateId: Long = 0,
    val timestamp: Long = currentTimeMillis()
)

@Serializable
data class DepthResponse(
    val lastUpdateId: Long,
    val bids: List<List<String>>,
    val asks: List<List<String>>,
    val E: Long? = null, // Message output time
    val T: Long? = null  // Transaction time
)
