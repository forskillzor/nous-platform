/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.tools.DrawingHistory
import com.aandios.nous.feature.chart.tools.DrawingToolType
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.ui.DefaultChartConfig

/**
 * Тонкая обёртка над [CandleStickChartInteraction].
 * Если передан footprintCandles — рисует footprint вместо свечей,
 * используя ту же логику взаимодействия, кросхаир, шкалы и линию цены.
 */
@Composable
fun CandleStickChart(
    candles: List<Candle>,
    currentPrice: Float? = null,
    modifier: Modifier = Modifier,
    config: ChartConfig = DefaultChartConfig,
    crosshairEnabled: Boolean = false,
    onNeedMoreHistory: () -> Unit = {},
    historyLoadCount: Int = 0,
    historyGeneration: Int = 0,
    hasMoreHistory: Boolean = true,
    footprintCandles: List<FootprintCandle>? = null,
    liquidationOrders: List<LiquidationOrder> = emptyList(),
    indicatorRenderers: List<DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit> = emptyList(),
    indicatorHeightDp: Dp = 80.dp,
    drawingHistory: DrawingHistory? = null,
    activeDrawingTool: DrawingToolType = DrawingToolType.NONE,
    onActiveDrawingToolChange: (DrawingToolType) -> Unit = {},
    initialZoomLevel: Float = 1f,
    onZoomChange: ((Float) -> Unit)? = null,
) {
    CandleStickChartInteraction(
        candles = candles,
        currentPrice = currentPrice,
        modifier = modifier,
        config = config,
        crosshairEnabled = crosshairEnabled,
        onNeedMoreHistory = onNeedMoreHistory,
        historyLoadCount = historyLoadCount,
        historyGeneration = historyGeneration,
        hasMoreHistory = hasMoreHistory,
        footprintCandles = footprintCandles,
        liquidationOrders = liquidationOrders,
        indicatorRenderers = indicatorRenderers,
        indicatorHeightDp = indicatorHeightDp,
        drawingHistory = drawingHistory,
        activeDrawingTool = activeDrawingTool,
        onActiveDrawingToolChange = onActiveDrawingToolChange,
        initialZoomLevel = initialZoomLevel,
        onZoomChange = onZoomChange,
    )
}
