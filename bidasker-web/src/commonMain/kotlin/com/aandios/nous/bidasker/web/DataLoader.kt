/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.bidasker.web

import com.aandios.nous.api.market.model.FootprintCandle
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
        val candles = apiClient.getFootprint(
            symbol = symbol,
            timeframe = timeframe,
            limit = limit
        )
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
