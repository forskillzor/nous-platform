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
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.tools.DrawingHistory
import com.aandios.nous.feature.chart.tools.DrawingToolType
import com.aandios.nous.feature.chart.ui.ChartConfig
import com.aandios.nous.feature.chart.ui.DefaultChartConfig

/**
 * Тонкая обёртка над [CandleStickChartInteraction].
 * Если передан footprintCandles — рисует footprint вместо свечей,
 * используя ту же логику взаимодействия, кросхаир, шкалы и линию цены.
 *
 * [tradingOrders] — линии открытых ордеров на графике (chart trading);
 * [onChartTradingClick] — клик по графику с ценой (размещение ордера).
 */

// todo дублирование сигнатур, раздуте кода, какой смысл???
@Composable
fun CandleStickChart(
    candles: List<Candle>,
    currentPrice: Float? = null,
    modifier: Modifier = Modifier,
    config: ChartConfig = DefaultChartConfig,
    onNeedMoreHistory: () -> Unit = {},
    hasMoreHistory: Boolean = true,
    footprintCandles: List<FootprintCandle>? = null,
    indicatorRenderers: List<DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit> = emptyList(),
    indicatorHeightDp: Dp = 80.dp,
    drawingHistory: DrawingHistory? = null,
    activeDrawingTool: DrawingToolType = DrawingToolType.NONE,
    onActiveDrawingToolChange: (DrawingToolType) -> Unit = {},
    initialZoomLevel: Float = 1f,
    onZoomChange: ((Float) -> Unit)? = null,
    tradingOrders: List<Order> = emptyList(),
    tradingPositions: List<com.aandios.nous.api.market.model.trading.Position> = emptyList(),
    symbolBase: String? = null,
    onChartTradingClick: ((Double) -> Unit)? = null,
    onCancelTradingOrder: ((Order) -> Unit)? = null,
    onMoveTradingOrder: ((Order, Double) -> Unit)? = null,
    onResizeTradingOrder: ((Order, Double) -> Unit)? = null,
    onCloseTradingPosition: ((com.aandios.nous.api.market.model.trading.Position) -> Unit)? = null,
) {
    CandleStickChartInteraction(
        candles = candles,
        currentPrice = currentPrice,
        modifier = modifier,
        config = config,
        onNeedMoreHistory = onNeedMoreHistory,
        hasMoreHistory = hasMoreHistory,
        footprintCandles = footprintCandles,
        indicatorRenderers = indicatorRenderers,
        indicatorHeightDp = indicatorHeightDp,
        drawingHistory = drawingHistory,
        activeDrawingTool = activeDrawingTool,
        onActiveDrawingToolChange = onActiveDrawingToolChange,
        initialZoomLevel = initialZoomLevel,
        onZoomChange = onZoomChange,
        tradingOrders = tradingOrders,
        tradingPositions = tradingPositions,
        symbolBase = symbolBase,
        onChartTradingClick = onChartTradingClick,
        onCancelTradingOrder = onCancelTradingOrder,
        onMoveTradingOrder = onMoveTradingOrder,
        onResizeTradingOrder = onResizeTradingOrder,
        onCloseTradingPosition = onCloseTradingPosition,
    )
}
