/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.ChartColors
import com.aandios.nous.feature.dom.domain.model.AggregationLevel

enum class ChartMode {
    CANDLESTICK,
    FOOTPRINT
}

// Конфигурация для отрисовки свечей
data class CandleStyle(
    val bullishColor: Color = ChartColors.bullish,
    val bearishColor: Color = ChartColors.bearish,
    val shadowColor: Color = ChartColors.candleShadow,
    val bodyWidth: Float = 10f,
    val shadowWidth: Float = 1f,
    val showShadows: Boolean = true,
    val showWicks: Boolean = true
)

// Конфигурация для footprint-чарта
data class FootprintConfig(
    val bidColor: Color = ChartColors.volumeBullish,
    val askColor: Color = ChartColors.volumeBearish,
    val showNumbers: Boolean = false,
    val maxLevelsPerCandle: Int = 50,
    val aggregationLevel: AggregationLevel = AggregationLevel.BaseTick,
    val tickSize: Double = 0.01
)

// Конфигурация для графика
data class ChartConfig(
    val backgroundColor: Color = ChartColors.chartBackground,
    val gridColor: Color = ChartColors.gridLine,
    val axisTextColor: Color = ChartColors.axisText,
    val showGrid: Boolean = true,
    val showVolume: Boolean = true,
    val showPriceScale: Boolean = true,
    val priceScaleWidth: Dp = 60.dp,
    val candleStyle: CandleStyle = CandleStyle(),
    val footprintConfig: FootprintConfig = FootprintConfig()
)

// Дефолтные настройки
val DefaultChartConfig = ChartConfig()