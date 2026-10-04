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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.v2.SwingWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.v2.rememberWindowStateWithBounds
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import com.aandios.nous.core.ui.window.applyWindowDarkBackground
import com.aandios.nous.feature.chart.di.initKoinForPreview
import com.aandios.nous.feature.chart.indicator.LiquidationViewModel
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.model.toSkeletonCandle
import com.aandios.nous.feature.chart.tools.DrawingHistory
import com.aandios.nous.feature.chart.tools.DrawingRepository
import com.aandios.nous.feature.chart.tools.DrawingToolType
import com.aandios.nous.feature.chart.ui.chart.CandleStickChart
import com.aandios.nous.feature.chart.ui.chart.drawLiquidationHistogram
import kotlinx.coroutines.launch
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.context.stopKoin

/**
 * Полноценное окно графика для использования внутри main приложения.
 * Получает ChartViewModel из Koin автоматически.
 * Восстанавливает символ/ТФ/режим/зум из той же БД, что и composeApp.
 */
@Composable
fun ChartWindow() {
    val chartViewModel: ChartViewModel = koinInject()
    val liquidationViewModel: LiquidationViewModel = koinInject()
    val previewScope = rememberCoroutineScope()

    val persistor = remember {
        runCatching {
            org.koin.core.context.GlobalContext.getOrNull()?.get<StateStore>()
        }.getOrNull()?.let { ChartStatePersistor(it) }
    }
    var initialZoom by remember { mutableStateOf(1f) }

    LaunchedEffect(Unit) {
        chartViewModel.dispatch(ChartIntent.RestoreState)
        val saved = persistor?.restore()
        initialZoom = persistor?.restoreZoom() ?: 1f
        chartViewModel.dispatch(ChartIntent.LoadChart(saved?.symbol, saved?.timeframe))
    }

    ChartWindowContent(
        chartViewModel = chartViewModel,
        liquidationViewModel = liquidationViewModel,
        initialZoomLevel = initialZoom,
        onZoomChange = { zoom ->
            persistor?.let { p -> previewScope.launch { p.saveZoom(zoom) } }
        },
    )
}

/**
 * Окно графика для использования внутри workspace-панелей (и др. композитов).
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
    val provider: Provider = koinInject()

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Верхняя панель — всегда видна, не зависит от состояния загрузки графика:
        // символ/ТФ/режим/агрегацию можно менять даже при Loading/Error.
        val exchanges = remember(provider) {
            runCatching {
                org.koin.core.context.GlobalContext.get().getAll<Provider>()
                    .map { it.config.displayName }.distinct()
            }.getOrNull() ?: listOf(provider.config.displayName)
        }
        var currentExchange by remember(provider) { mutableStateOf(provider.config.displayName) }

        ChartToolbar(
            currentSymbol = uiState.currentSymbol,
            currentTimeframe = uiState.currentTimeframe,
            availableSymbols = uiState.symbols,
            onSymbolChange = { chartViewModel.dispatch(ChartIntent.SelectSymbol(it)) },
            onTimeframeChange = { chartViewModel.dispatch(ChartIntent.SelectTimeframe(it)) },
            exchanges = exchanges,
            currentExchange = currentExchange,
            onExchangeChange = { currentExchange = it },
            chartMode = uiState.chartMode,
            onChartModeChange = { chartViewModel.dispatch(ChartIntent.SelectChartMode(it)) },
            symbolsWithFootprint = uiState.symbolsWithFootprint,
            fpAggregation = uiState.fpAggregation,
            onFpAggregationChange = { chartViewModel.dispatch(ChartIntent.SetFpAggregation(it)) },
            modifier = Modifier.padding(8.dp)
        )

        // Область графика: Loading/Error — только внутри неё.
        // Панель рисования и водяной знак видны всегда.
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Водяной знак символа — самый нижний слой, ничего не перекрывает:
            // крупный тикер монеты + биржа и тип контракта (как в TradingView)
            val symbolInfo = uiState.currentSymbolInfo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp, top = 4.dp)
            ) {
                Text(
                    text = symbolInfo?.baseAsset ?: uiState.currentSymbol,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                )
                if (symbolInfo != null) {
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = provider.config.displayName,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        contractTypeLabel(symbolInfo)?.let { label ->
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            when (uiState.chartMode) {
                ChartMode.CANDLESTICK -> {
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
                            CandleStickChart(
                                candles = state.candles,
                                currentPrice = state.currentPrice,
                                config = chartConfig,
                                liquidationOrders = liquidationState.orders,
                                indicatorRenderers = indicatorRenderers,
                                onNeedMoreHistory = { chartViewModel.dispatch(ChartIntent.LoadMoreHistory) },
                                hasMoreHistory = uiState.hasMoreHistory,
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
                            footprintCandles = allFp,
                            onNeedMoreHistory = { chartViewModel.dispatch(ChartIntent.LoadMoreFootprintHistory) },
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

            DrawingToolPanel(
                activeTool = activeDrawingTool,
                onToolChange = { activeDrawingTool = it },
                canUndo = drawingHistory.canUndo,
                canRedo = drawingHistory.canRedo,
                onUndo = { drawingHistory.undo() },
                onRedo = { drawingHistory.redo() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    stopKoin()
    initKoinForPreview()

    SwingWindow(
        onCloseRequest = ::exitApplication,
        title = "Nous Platform • Chart Preview",
        state = rememberWindowStateWithBounds(initialSize = DpSize(800.dp, 600.dp)),
        // До показа окна: тёмный фон AWT-фрейма — без белых вспышек при ресайзе.
        init = { w -> applyWindowDarkBackground(w) },
    ) {
        KoinContext {
            TradingTerminalTheme {
                ChartWindow()
            }
        }
    }
}
