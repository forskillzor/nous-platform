/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import com.aandios.nous.feature.chart.di.initKoinForPreview
import com.aandios.nous.feature.chart.indicator.LiquidationViewModel
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.model.toSkeletonCandle
import com.aandios.nous.feature.chart.tools.DrawingHistory
import com.aandios.nous.feature.chart.tools.DrawingRepository
import com.aandios.nous.feature.chart.tools.DrawingToolType
import com.aandios.nous.feature.chart.ui.chart.CandleStickChart
import com.aandios.nous.feature.chart.ui.chart.drawLiquidationHistogram
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.context.stopKoin

/**
 * Полноценное окно графика для использования внутри main приложения.
 * Получает ChartViewModel из Koin автоматически.
 */
@Composable
fun ChartWindow() {
    val chartViewModel: ChartViewModel = koinInject()
    LaunchedEffect(Unit) {
        chartViewModel.dispatch(ChartIntent.LoadChart())
    }
    val liquidationViewModel: LiquidationViewModel = koinInject()
    ChartWindowContent(
        chartViewModel = chartViewModel,
        liquidationViewModel = liquidationViewModel
    )
}

/**
 * Окно графика для использования внутри MainScreen (и др. композитов).
 * Принимает ChartViewModel напрямую (чтобы не плодить лишних экземпляров при factory-scope).
 *
 * Загрузку графика (dispatch(LoadChart)) ожидается, что вызывает родительский composable.
 */
@Composable
fun ChartWindow(
    chartViewModel: ChartViewModel,
    modifier: Modifier = Modifier,
    initialZoomLevel: Float = 1f,
    onZoomChange: ((Float) -> Unit)? = null,
    workspaceId: String? = null,
    panelId: String? = null,
) {
    val liquidationViewModel: LiquidationViewModel = koinInject()
    ChartWindowContent(
        chartViewModel = chartViewModel,
        modifier = modifier,
        liquidationViewModel = liquidationViewModel,
        initialZoomLevel = initialZoomLevel,
        onZoomChange = onZoomChange,
        workspaceId = workspaceId,
        panelId = panelId,
    )
}

@Composable
private fun ChartWindowContent(
    chartViewModel: ChartViewModel,
    modifier: Modifier = Modifier,
    liquidationViewModel: LiquidationViewModel,
    initialZoomLevel: Float = 1f,
    onZoomChange: ((Float) -> Unit)? = null,
    workspaceId: String? = null,
    panelId: String? = null,
) {
    val uiState by chartViewModel.state.collectAsState()

    val chartConfig = remember(uiState.fpAggregation, uiState.currentSymbolFormatter) {
        DefaultChartConfig.copy(
            footprintConfig = DefaultChartConfig.footprintConfig.copy(
                aggregationLevel = uiState.fpAggregation,
                tickSize = uiState.currentSymbolFormatter.tickSize
            ),
            priceFormatter = uiState.currentSymbolFormatter
        )
    }

    // Liquidation state
    val liquidationState by liquidationViewModel.state.collectAsState()
    LaunchedEffect(uiState.currentSymbol) {
        liquidationViewModel.subscribe(uiState.currentSymbol)
    }
    DisposableEffect(Unit) {
        onDispose { liquidationViewModel.clear() }
    }

    val liqOrders = liquidationState.orders
    val indicatorRenderers = remember(liqOrders) {
        listOf<DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit>(
            { area, candles, _, scroll, zoom ->
                drawLiquidationHistogram(area, candles, liqOrders, scroll, zoom)
            }
        )
    }

    var crosshairEnabled by remember { mutableStateOf(false) }

    // Drawings: привязаны к workspace+panel и сохраняются на диск
    val drawingStore: StateStore? = remember {
        runCatching { org.koin.core.context.GlobalContext.getOrNull()?.get<StateStore>() }.getOrNull()
    }
    val drawingHistory = remember { DrawingHistory() }
    var activeDrawingTool by remember { mutableStateOf(DrawingToolType.NONE) }

    LaunchedEffect(workspaceId, panelId, drawingStore) {
        val store = drawingStore
        if (workspaceId == null || panelId == null || store == null) return@LaunchedEffect
        val repository = DrawingRepository(store)
        drawingHistory.replaceAll(repository.load(workspaceId, panelId))
        snapshotFlow { drawingHistory.drawings.toList() }
            .collect { repository.save(workspaceId, panelId, it) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState.chartState) {
            is ChartState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading chart data...",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp
                    )
                }
            }
            is ChartState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Error loading chart",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp
                        )
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            is ChartState.Success -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (uiState.chartMode) {
                        ChartMode.CANDLESTICK -> {
                            CandleStickChart(
                                candles = state.candles,
                                currentPrice = state.currentPrice,
                                config = chartConfig,
                                liquidationOrders = liquidationState.orders,
                                indicatorRenderers = indicatorRenderers,
                                crosshairEnabled = crosshairEnabled,
                                onNeedMoreHistory = { chartViewModel.dispatch(ChartIntent.LoadMoreHistory) },
                                historyLoadCount = uiState.historyLoadCount,
                                hasMoreHistory = uiState.hasMoreHistory,
                                drawingHistory = drawingHistory,
                                activeDrawingTool = activeDrawingTool,
                                onActiveDrawingToolChange = { activeDrawingTool = it },
                                modifier = Modifier.fillMaxSize(),
                                initialZoomLevel = initialZoomLevel,
                                onZoomChange = onZoomChange,
                            )
                        }
                        ChartMode.FOOTPRINT -> {
                            if (uiState.footprintLoading && uiState.footprintCandles.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Loading footprint data...", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                                }
                            } else if (uiState.footprintError != null && uiState.footprintCandles.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Footprint data error", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                                        Text(uiState.footprintError!!, color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            } else {
                                // Merge completed + live: live overrides same startTime
                                val allFp = buildList {
                                    val liveStart = uiState.liveFootprintCandle?.startTime
                                    addAll(uiState.footprintCandles.filter { it.startTime != liveStart })
                                    uiState.liveFootprintCandle?.let { add(it) }
                                }
                                val fpToCandle = remember(allFp) {
                                    allFp.map { it.toSkeletonCandle() }
                                }
                                CandleStickChart(
                                    candles = fpToCandle,
                                    currentPrice = uiState.footprintCurrentPrice ?: fpToCandle.lastOrNull()?.close,
                                    config = chartConfig,
                                    liquidationOrders = liquidationState.orders,
                                    indicatorRenderers = indicatorRenderers,
                                    crosshairEnabled = crosshairEnabled,
                                    footprintCandles = allFp,
                                    onNeedMoreHistory = { chartViewModel.dispatch(ChartIntent.LoadMoreFootprintHistory) },
                                    historyLoadCount = uiState.footprintHistoryLoadCount,
                                    hasMoreHistory = uiState.hasMoreFootprintHistory,
                                    drawingHistory = drawingHistory,
                                    activeDrawingTool = activeDrawingTool,
                                    onActiveDrawingToolChange = { activeDrawingTool = it },
                                    modifier = Modifier.fillMaxSize(),
                                    initialZoomLevel = initialZoomLevel,
                                    onZoomChange = onZoomChange,
                                )
                            }
                        }
                    }

                    ChartToolbar(
                        currentSymbol = uiState.currentSymbol,
                        currentTimeframe = uiState.currentTimeframe,
                        availableSymbols = uiState.symbols,
                        onSymbolChange = { chartViewModel.dispatch(ChartIntent.SelectSymbol(it)) },
                        onTimeframeChange = { chartViewModel.dispatch(ChartIntent.SelectTimeframe(it)) },
                        crosshairEnabled = crosshairEnabled,
                        onCrosshairToggle = { crosshairEnabled = !crosshairEnabled },
                        chartMode = uiState.chartMode,
                        onChartModeToggle = { chartViewModel.dispatch(ChartIntent.ToggleChartMode) },
                        symbolsWithFootprint = uiState.symbolsWithFootprint,
                        fpAggregation = uiState.fpAggregation,
                        onFpAggregationChange = { chartViewModel.dispatch(ChartIntent.SetFpAggregation(it)) },
                        drawingTool = activeDrawingTool,
                        onDrawingToolChange = { activeDrawingTool = it },
                        canUndoDrawing = drawingHistory.canUndo,
                        canRedoDrawing = drawingHistory.canRedo,
                        onUndoDrawing = { drawingHistory.undo() },
                        onRedoDrawing = { drawingHistory.redo() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

fun main() = application {
    stopKoin()
    initKoinForPreview()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Nous Platform • Chart Preview",
        state = rememberWindowState(width = 800.dp, height = 600.dp)
    ) {
        KoinContext {
            TradingTerminalTheme {
                ChartWindow()
            }
        }
    }
}
