/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model

import kotlinx.serialization.Serializable

@Serializable
data class FootprintLevel(
    val price: String,
    val bidVolume: String,
    val askVolume: String,
    val bidCount: Int = 0,
    val askCount: Int = 0
) {
    val priceFloat: Float get() = price.toFloatOrNull() ?: 0f
    val bidVolumeFloat: Float get() = bidVolume.toFloatOrNull() ?: 0f
    val askVolumeFloat: Float get() = askVolume.toFloatOrNull() ?: 0f
}

@Serializable
data class FootprintCandle(
    val exchange: String = "",
    val symbol: String = "",
    val timeframe: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val totalTicks: Long = 0L,
    val minPrice: String = "0",
    val maxPrice: String = "0",
    val levels: List<FootprintLevel> = emptyList()
) {
    val open: Float get() = levels.firstOrNull()?.priceFloat ?: 0f
    val high: Float get() = maxPrice.toFloatOrNull() ?: 0f
    val low: Float get() = minPrice.toFloatOrNull() ?: 0f
    val close: Float get() = levels.lastOrNull()?.priceFloat ?: 0f
    val maxVolume: Float get() = levels.maxOfOrNull { maxOf(it.bidVolumeFloat, it.askVolumeFloat) } ?: 0f
}

/**
 * Live-accumulated footprint candle for the current minute.
 * Not serializable — built in memory from real-time trades.
 */
class MutableFootprintCandle(
    val exchange: String = "Binance",
    val symbol: String = "",
    val timeframe: String = "1m",
    val startTime: Long = 0L,
    val endTime: Long = 0L
) {
    private val levelMap = linkedMapOf<Float, MutableLevel>()
    var lastPrice: Float = 0f
        private set

    data class MutableLevel(
        var bidVolume: Float = 0f,
        var askVolume: Float = 0f,
        var bidCount: Int = 0,
        var askCount: Int = 0
    )

    fun addTrade(price: Float, quantity: Float, isBuy: Boolean) {
        lastPrice = price
        val level = levelMap.getOrPut(price) { MutableLevel() }
        if (isBuy) {
            level.bidVolume += quantity
            level.bidCount++
        } else {
            level.askVolume += quantity
            level.askCount++
        }
    }

    fun clear() {
        levelMap.clear()
        lastPrice = 0f
    }

    fun toFootprintCandle(totalTicks: Long = 0L): FootprintCandle {
        val sortedLevels = levelMap.entries.sortedBy { it.key }
        if (sortedLevels.isEmpty()) return FootprintCandle(
            exchange = exchange, symbol = symbol, timeframe = timeframe,
            startTime = startTime, endTime = endTime, totalTicks = totalTicks,
            levels = emptyList()
        )
        val levels = sortedLevels.map { (price, ml) ->
            FootprintLevel(
                price = price.toString(),
                bidVolume = ml.bidVolume.toString(),
                askVolume = ml.askVolume.toString(),
                bidCount = ml.bidCount,
                askCount = ml.askCount
            )
        }
        val minP = sortedLevels.first().key.toString()
        val maxP = sortedLevels.last().key.toString()
        return FootprintCandle(
            exchange = exchange, symbol = symbol, timeframe = timeframe,
            startTime = startTime, endTime = endTime,
            totalTicks = totalTicks,
            minPrice = minP, maxPrice = maxP,
            levels = levels
        )
    }

    fun getCurrentPrice(): Float? = levelMap.keys.maxOrNull()
    fun getMinPrice(): Float? = levelMap.keys.minOrNull()
    fun getMaxPrice(): Float? = levelMap.keys.maxOrNull()
}
