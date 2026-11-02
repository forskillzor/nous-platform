/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.api.market.model.orderbook.PriceUpdate
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal fun hyperliquidCandle(row: JsonObject): Candle? {
    val timestamp = row.long("t") ?: row.long("T") ?: return null
    val open = row.double("o")?.toFloat() ?: return null
    val high = row.double("h")?.toFloat() ?: return null
    val low = row.double("l")?.toFloat() ?: return null
    val close = row.double("c")?.toFloat() ?: return null
    val volume = row.double("v")?.toFloat() ?: 0f
    return Candle(
        open = open,
        high = high,
        close = close,
        low = low,
        timestamp = timestamp,
        volume = volume,
    )
}

internal fun hyperliquidTrade(row: JsonObject): Trade? {
    val coin = row.string("coin") ?: return null
    val price = row.double("px") ?: return null
    val quantity = row.double("sz") ?: return null
    val timestamp = row.long("time") ?: return null
    val side = when (row.string("side")) {
        "B" -> TradeSide.BUY
        "A" -> TradeSide.SELL
        else -> return null
    }
    val rawId = row.long("tid")
        ?: row.string("hash")?.hashCode()?.toLong()?.and(0xFFFFFFFFL)
        ?: "$coin-$timestamp-$price-$quantity".hashCode().toLong().and(0xFFFFFFFFL)
    return Trade(
        id = rawId,
        symbol = coin,
        price = price,
        quantity = quantity,
        timestamp = timestamp,
        isBuyerMaker = side == TradeSide.SELL,
        side = side,
    )
}

internal fun hyperliquidBookWindow(data: JsonObject, depth: Int): BookWindowLevels {
    val levels = data["levels"]?.asArray() ?: JsonArray(emptyList())
    val bids = levels.getOrNull(0)?.asArray()
    val asks = levels.getOrNull(1)?.asArray()
    return BookWindowLevels(
        bids = hyperliquidLevels(bids, depth),
        asks = hyperliquidLevels(asks, depth),
    )
}

internal fun hyperliquidBbo(data: JsonObject): BookTicker? {
    val coin = data.string("coin") ?: return null
    val bbo = data["bbo"]?.asArray() ?: return null
    val bid = hyperliquidLevel(bbo.getOrNull(0))
    val ask = hyperliquidLevel(bbo.getOrNull(1))
    if (bid == null || ask == null) return null
    val mid = (bid.price + ask.price) / 2.0
    return BookTicker(
        symbol = coin,
        bestBid = bid.price,
        bestBidQty = bid.quantity,
        bestAsk = ask.price,
        bestAskQty = ask.quantity,
        lastPrice = mid,
        timestamp = data.long("time") ?: hyperliquidCurrentTimeMillis(),
    )
}

internal fun hyperliquidBookTickerFromL2(data: JsonObject): BookTicker? {
    val coin = data.string("coin") ?: return null
    val levels = data["levels"]?.asArray() ?: return null
    val bid = hyperliquidLevel(levels.getOrNull(0)?.asArray()?.firstOrNull())
    val ask = hyperliquidLevel(levels.getOrNull(1)?.asArray()?.firstOrNull())
    if (bid == null || ask == null) return null
    val mid = (bid.price + ask.price) / 2.0
    return BookTicker(
        symbol = coin,
        bestBid = bid.price,
        bestBidQty = bid.quantity,
        bestAsk = ask.price,
        bestAskQty = ask.quantity,
        lastPrice = mid,
        timestamp = data.long("time") ?: hyperliquidCurrentTimeMillis(),
    )
}

private fun hyperliquidLevels(rows: JsonArray?, depth: Int): List<PriceUpdate> {
    if (rows == null || depth <= 0) return emptyList()
    val result = ArrayList<PriceUpdate>(minOf(rows.size, depth))
    rows.forEach { row ->
        if (result.size >= depth) return@forEach
        hyperliquidLevel(row)?.let { result += it }
    }
    return result
}

private fun hyperliquidLevel(element: kotlinx.serialization.json.JsonElement?): PriceUpdate? {
    val row = element?.asObject() ?: return null
    val price = row.double("px") ?: return null
    val quantity = row.double("sz") ?: return null
    return PriceUpdate(price, quantity)
}
