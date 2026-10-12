/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun bybitInterval(interval: String): String {
    if (interval == "1M") return "M"
    return when (interval.lowercase()) {
        "1m" -> "1"
        "3m" -> "3"
        "5m" -> "5"
        "15m" -> "15"
        "30m" -> "30"
        "1h" -> "60"
        "2h" -> "120"
        "4h" -> "240"
        "6h" -> "360"
        "12h" -> "720"
        "1d", "d" -> "D"
        "1w", "w" -> "W"
        "1mo", "m" -> "M"
        else -> interval
    }
}

internal fun bybitBookDepth(requested: Int): Int = when {
    requested <= 1 -> 1
    requested <= 50 -> 50
    requested <= 200 -> 200
    else -> 1_000
}

internal fun bybitKline(row: JsonArray): Candle? {
    if (row.size < 6) return null
    val values = row.map { it.jsonPrimitive.contentOrNull }
    val timestamp = values[0]?.toLongOrNull() ?: return null
    val open = values[1]?.toFloatOrNull() ?: return null
    val high = values[2]?.toFloatOrNull() ?: return null
    val low = values[3]?.toFloatOrNull() ?: return null
    val close = values[4]?.toFloatOrNull() ?: return null
    val volume = values[5]?.toFloatOrNull() ?: return null
    return Candle(open = open, high = high, low = low, close = close, timestamp = timestamp, volume = volume)
}

internal fun bybitKline(row: JsonObject): Candle? {
    val timestamp = row.long("start") ?: return null
    val open = row.double("open")?.toFloat() ?: return null
    val high = row.double("high")?.toFloat() ?: return null
    val low = row.double("low")?.toFloat() ?: return null
    val close = row.double("close")?.toFloat() ?: return null
    val volume = row.double("volume")?.toFloat() ?: return null
    return Candle(open = open, high = high, low = low, close = close, timestamp = timestamp, volume = volume)
}

internal fun bybitTrade(row: JsonObject): Trade? {
    val symbol = row.string("s") ?: return null
    val price = row.double("p") ?: return null
    val quantity = row.double("v") ?: return null
    val timestamp = row.long("T") ?: row.long("time") ?: return null
    val sideText = row.string("S") ?: row.string("side") ?: return null
    val executionId = row.string("i") ?: row.string("execId") ?: "$symbol-$timestamp-$price-$quantity"
    val side = sideText.toTradeSide() ?: return null
    return Trade(
        id = executionId.toLongOrNull() ?: (executionId.hashCode().toLong() and 0xFFFFFFFFL),
        symbol = symbol,
        price = price,
        quantity = quantity,
        timestamp = timestamp,
        isBuyerMaker = side == TradeSide.SELL,
        side = side,
    )
}

internal fun bybitLiquidation(row: JsonObject): LiquidationOrder? {
    val symbol = row.string("s") ?: return null
    val price = row.double("p") ?: return null
    val quantity = row.double("v") ?: return null
    val timestamp = row.long("T") ?: return null
    val side = row.string("S")?.toTradeSide() ?: return null
    return LiquidationOrder(
        symbol = symbol,
        price = price,
        quantity = quantity,
        timestamp = timestamp,
        side = side,
        orderType = "LIQUIDATION",
    )
}

internal fun bybitBookTicker(row: JsonObject, timestamp: Long, previous: BookTicker? = null): BookTicker? {
    val symbol = row.string("symbol") ?: previous?.symbol ?: return null
    val bid = row.double("bid1Price") ?: previous?.bestBid ?: return null
    val bidQty = row.double("bid1Size") ?: previous?.bestBidQty ?: return null
    val ask = row.double("ask1Price") ?: previous?.bestAsk ?: return null
    val askQty = row.double("ask1Size") ?: previous?.bestAskQty ?: return null
    val last = row.double("lastPrice") ?: previous?.lastPrice ?: (bid + ask) / 2.0
    return BookTicker(
        symbol = symbol,
        bestBid = bid,
        bestBidQty = bidQty,
        bestAsk = ask,
        bestAskQty = askQty,
        lastPrice = last,
        timestamp = timestamp,
    )
}

internal fun bybitBookWindow(
    bids: Map<Double, Double>,
    asks: Map<Double, Double>,
    depth: Int,
) = BookWindowLevels(
    bids = bids.entries.sortedByDescending { it.key }.take(depth)
        .map { (price, qty) -> com.aandios.nous.api.market.model.orderbook.PriceUpdate(price, qty) },
    asks = asks.entries.sortedBy { it.key }.take(depth)
        .map { (price, qty) -> com.aandios.nous.api.market.model.orderbook.PriceUpdate(price, qty) },
)

private fun String.toTradeSide(): TradeSide? = when (lowercase()) {
    "buy" -> TradeSide.BUY
    "sell" -> TradeSide.SELL
    else -> null
}

internal fun JsonElement.asObjectList(): List<JsonObject> =
    (this as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
