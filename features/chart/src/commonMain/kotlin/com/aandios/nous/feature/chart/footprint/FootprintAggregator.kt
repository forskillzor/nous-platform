/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.footprint

import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.FootprintLevel

/**
 * Чистая логика footprint: выбор источника таймфрейма и агрегация свечей.
 * Вынесено из companion ChartViewModel для тестируемости.
 */
object FootprintAggregator {

    /**
     * Возвращает исходный таймфрейм сервера и количество свечей для агрегации
     * в отображаемый таймфрейм.
     */
    fun resolveFootprintSourceTimeframe(displayTimeframe: String): Pair<String, Int> {
        return when (displayTimeframe) {
            "1m" -> "1m" to 1
            "5m" -> "1m" to 5
            "15m" -> "15m" to 1
            "30m" -> "15m" to 2
            "1h" -> "15m" to 4
            "4h" -> "15m" to 16
            "1d" -> "15m" to 96
            "1w" -> "15m" to 672
            else -> "1m" to 1
        }
    }

    /**
     * Агрегирует свечи по count штук: суммирует bid/ask объёмы и количества по ценовым уровням.
     */
    fun aggregateFootprintCandles(candles: List<FootprintCandle>, count: Int): List<FootprintCandle> {
        if (count <= 1 || candles.isEmpty()) return candles

        data class Acc(var bidVol: Float = 0f, var askVol: Float = 0f, var bidCnt: Int = 0, var askCnt: Int = 0)

        return candles.chunked(count)
            .filter { it.isNotEmpty() }
            .map { group ->
                val startTime = group.first().startTime
                val endTime = group.last().endTime
                val minPrice = group.minOfOrNull { it.minPrice.toDoubleOrNull() ?: Double.MAX_VALUE } ?: 0.0
                val maxPrice = group.maxOfOrNull { it.maxPrice.toDoubleOrNull() ?: Double.MIN_VALUE } ?: 0.0
                val totalTicks = group.sumOf { it.totalTicks }

                val levelMap = linkedMapOf<String, Acc>()
                for (candle in group) {
                    for (level in candle.levels) {
                        val acc = levelMap.getOrPut(level.price) { Acc() }
                        acc.bidVol += level.bidVolumeFloat
                        acc.askVol += level.askVolumeFloat
                        acc.bidCnt += level.bidCount
                        acc.askCnt += level.askCount
                    }
                }

                val sorted = levelMap.entries.sortedByDescending { it.key.toDoubleOrNull() ?: 0.0 }
                val levels = sorted.map { (price, acc) ->
                    FootprintLevel(
                        price = price,
                        bidVolume = acc.bidVol.toString(),
                        askVolume = acc.askVol.toString(),
                        bidCount = acc.bidCnt,
                        askCount = acc.askCnt
                    )
                }
                FootprintCandle(
                    exchange = group.first().exchange,
                    symbol = group.first().symbol,
                    timeframe = group.first().timeframe,
                    startTime = startTime,
                    endTime = endTime,
                    totalTicks = totalTicks,
                    minPrice = minPrice.toString(),
                    maxPrice = maxPrice.toString(),
                    levels = levels
                )
            }
    }
}
