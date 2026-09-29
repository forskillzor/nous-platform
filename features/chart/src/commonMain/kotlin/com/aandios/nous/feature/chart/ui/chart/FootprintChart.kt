/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.feature.chart.model.toSkeletonCandle
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.ui.DefaultChartConfig

/**
 * Standalone footprint-график для bidasker-web.
 *
 * Тонкая обёртка над общим движком: footprint-свечи конвертируются в
 * каркасные [com.aandios.nous.api.market.model.Candle], а все жесты,
 * скролл, зум и шкалы предоставляет [CandleStickChartInteraction]
 * (режим footprint). Отдельной реализации скролла/зума здесь больше нет.
 */
@Composable
fun FootprintChart(
    completedCandles: List<FootprintCandle>,
    liveCandle: FootprintCandle? = null,
    currentPrice: Float? = null,
    modifier: Modifier = Modifier,
    config: ChartConfig = DefaultChartConfig,
    crosshairEnabled: Boolean = false,
) {
    val allCandles = remember(completedCandles, liveCandle) {
        if (liveCandle != null) completedCandles + liveCandle else completedCandles
    }

    if (allCandles.isEmpty()) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            Text(text = "No footprint data available.", color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    val skeleton = remember(allCandles) { allCandles.map { it.toSkeletonCandle() } }

    CandleStickChart(
        candles = skeleton,
        currentPrice = currentPrice ?: skeleton.lastOrNull()?.close,
        modifier = modifier,
        config = config,
        crosshairEnabled = crosshairEnabled,
        footprintCandles = allCandles,
        hasMoreHistory = false,
    )
}
