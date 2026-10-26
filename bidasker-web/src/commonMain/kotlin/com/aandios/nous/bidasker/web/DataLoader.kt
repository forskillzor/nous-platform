/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.bidasker.web

import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.feature.chart.footprint.FootprintAggregator
import com.aandios.nous.feature.chart.footprint.FootprintApiClient

suspend fun loadCandles(
    apiClient: FootprintApiClient,
    symbol: String,
    timeframe: String,
    tariff: TariffConfig,
    onResult: (List<FootprintCandle>) -> Unit
) {
    try {
        val maxHours = tariff.limits.maxHistoryHours
        val limit = when {
            maxHours <= 0 -> 200
            else -> maxOf(10, maxHours * 60 / timeframeToMinutes(timeframe))
        }
        val (sourceTimeframe, aggregateCount) =
            FootprintAggregator.resolveFootprintSourceTimeframe(timeframe)
        val sourceLimit = limit * aggregateCount
        val sourceCandles = apiClient.getFootprint(
            symbol = symbol,
            timeframe = sourceTimeframe,
            limit = sourceLimit
        )
        val candles = if (aggregateCount > 1) {
            FootprintAggregator.aggregateFootprintCandles(
                sourceCandles,
                aggregateCount,
                FootprintAggregator.sourceTimeframeMs(sourceTimeframe),
                timeframe,
            ).take(limit)
        } else {
            sourceCandles
        }
        onResult(candles)
    } catch (e: Exception) {
        onResult(emptyList())
    }
}

suspend fun loadAvailableSymbols(
    apiClient: FootprintApiClient,
    onResult: (List<String>) -> Unit
) {
    try {
        val instruments = apiClient.getInstruments()
        onResult(instruments.map { it.symbol })
    } catch (e: Exception) {
        onResult(listOf("BTCUSDT", "ETHUSDT"))
    }
}

private fun timeframeToMinutes(tf: String): Int = when (tf) {
    "1m" -> 1; "5m" -> 5; "15m" -> 15; "30m" -> 30
    "1h" -> 60; "4h" -> 240; "1d" -> 1440; "1w" -> 10080
    else -> 5
}
