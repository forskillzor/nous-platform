/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.core.ui.theme.ChartColors
import com.aandios.nous.core.ui.theme.TerminalDesignTokens
import com.aandios.nous.feature.dom.domain.model.AggregationLevel

enum class ChartMode {
    CANDLESTICK,
    FOOTPRINT
}

/** Перцентиль объёмов, по которому нормируется ширина Profile-баров. */
enum class FootprintProfileScale(val percentile: Float, val label: String) {
    P90(0.90f, "P90"),
    P95(0.95f, "P95"),
    P99(0.99f, "P99"),
    MAX(1f, "MAX");

    companion object {
        fun fromString(raw: String): FootprintProfileScale? =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            }
    }
}

/** Толщина кольца, которым отмечается выброс Profile. */
enum class FootprintRingWidth(val width: Dp, val label: String) {
    HALF(0.5.dp, "0.5"),
    ONE(1.dp, "1"),
    ONE_HALF(1.5.dp, "1.5"),
    TWO(2.dp, "2"),
    TWO_HALF(2.5.dp, "2.5"),
    THREE(3.dp, "3"),
    THREE_HALF(3.5.dp, "3.5"),
    FOUR(4.dp, "4"),
    FOUR_HALF(4.5.dp, "4.5"),
    FIVE(5.dp, "5");

    companion object {
        fun fromString(raw: String): FootprintRingWidth? =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            }
    }
}

/** Диаметр кольца выброса относительно ширины свечи (1.0 = 1x). */
enum class FootprintRingDiameter(val ratio: Float, val label: String) {
    X1(1f, "1x"),
    X1_5(1.5f, "1.5x"),
    X2(2f, "2x"),
    X2_5(2.5f, "2.5x"),
    X3(3f, "3x"),
    X3_5(3.5f, "3.5x"),
    X4(4f, "4x"),
    X4_5(4.5f, "4.5x"),
    X5(5f, "5x"),
    X5_5(5.5f, "5.5x"),
    X6(6f, "6x"),
    X6_5(6.5f, "6.5x"),
    X7(7f, "7x"),
    X7_5(7.5f, "7.5x"),
    X8(8f, "8x"),
    X8_5(8.5f, "8.5x"),
    X9(9f, "9x"),
    X9_5(9.5f, "9.5x"),
    X10(10f, "10x");

    companion object {
        fun fromString(raw: String): FootprintRingDiameter? {
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            }?.let { return it }
            return when (raw.uppercase()) {
                "CANDLE", "W100", "100%" -> X1
                "W125", "W150", "125%", "150%" -> X1_5
                "W175", "W200", "175%", "200%" -> X2
                "W250", "250%" -> X2_5
                "W300", "300%" -> X3
                "W350", "350%" -> X3_5
                "W400", "400%" -> X4
                "W450", "450%" -> X4_5
                "W500", "500%" -> X5
                else -> null
            }
        }
    }
}

// Конфигурация для отрисовки свечей
data class CandleStyle(
    val bullishColor: Color = ChartColors.bullish,
    val bearishColor: Color = ChartColors.bearish,
    val shadowColor: Color = ChartColors.candleShadow,
    val shadowWidth: Float = 1f,
    val showShadows: Boolean = true
)

// Конфигурация для footprint-чарта
data class FootprintConfig(
    val bidColor: Color = ChartColors.volumeBullish,
    val askColor: Color = ChartColors.volumeBearish,
    val aggregationLevel: AggregationLevel = AggregationLevel.BaseTick,
    val tickSize: Double = 0.01,
    val profileScale: FootprintProfileScale = FootprintProfileScale.P99,
    val ringWidth: FootprintRingWidth = FootprintRingWidth.TWO,
    val ringDiameter: FootprintRingDiameter = FootprintRingDiameter.X3,
)

// Конфигурация для графика
data class ChartConfig(
    val backgroundColor: Color = ChartColors.chartBackground,
    val gridColor: Color = ChartColors.gridLine,
    val majorGridColor: Color = ChartColors.gridLineMajor,
    val axisTextColor: Color = ChartColors.axisText,
    val currentPriceColor: Color = ChartColors.bullish,
    val crosshairColor: Color = ChartColors.crosshair,
    val accentColor: Color = ChartColors.bullish,
    val pnlPlateColor: Color = ChartColors.orderBackground,
    val showGrid: Boolean = true,
    val showPriceScale: Boolean = true,
    val priceScaleWidth: Dp = 60.dp,
    val candleStyle: CandleStyle = CandleStyle(),
    val footprintConfig: FootprintConfig = FootprintConfig(),
    val priceFormatter: SymbolFormatter = SymbolFormatter.DEFAULT,
    // Границы зума (позже войдут в сериализуемый конфиг / окно настроек)
    val minZoom: Float = 0.05f,
    val maxZoom: Float = 4f,
    val maxZoomFootprint: Float = 30f
)

fun ChartConfig.withTheme(tokens: TerminalDesignTokens): ChartConfig = copy(
    backgroundColor = tokens.colors.chartBackground,
    gridColor = tokens.colors.gridLine,
    majorGridColor = tokens.colors.gridLineMajor,
    axisTextColor = tokens.colors.axisText,
    currentPriceColor = tokens.colors.accent,
    crosshairColor = tokens.colors.crosshair,
    accentColor = tokens.colors.accent,
    pnlPlateColor = tokens.colors.pnlPlate,
    candleStyle = candleStyle.copy(
        bullishColor = tokens.colors.bullish,
        bearishColor = tokens.colors.bearish,
        shadowColor = tokens.colors.candleShadow,
    ),
    footprintConfig = footprintConfig.copy(
        bidColor = tokens.colors.volumeBullish,
        askColor = tokens.colors.volumeBearish,
    ),
)

// Дефолтные настройки
val DefaultChartConfig = ChartConfig()
