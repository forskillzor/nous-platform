/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.core.domain.timeseries.Timeframes
import com.aandios.nous.core.ui.theme.TerminalFontFamily

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.v2.rememberWindowStateWithBounds
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.core.ui.component.TerminalPanelHeader
import com.aandios.nous.core.ui.format.plainDecimalString
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.ui.window.applyWindowBackground
import com.aandios.nous.core.ui.window.applyWindowsDarkTitleBar
import com.aandios.nous.feature.chart.di.initKoinForPreview
import com.aandios.nous.feature.chart.indicator.ChartIndicatorInputSource
import com.aandios.nous.feature.chart.indicator.LiquidationHistorySource
import com.aandios.nous.feature.chart.model.ChartLayout
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.model.toSkeletonCandle
import com.aandios.nous.feature.chart.tools.DrawingHistory
import com.aandios.nous.feature.chart.tools.DrawingRepository
import com.aandios.nous.feature.chart.tools.DrawingToolType
import com.aandios.nous.feature.chart.ui.chart.CandleStickChart
import com.aandios.nous.feature.chart.ui.chart.IndicatorChartColors
import com.aandios.nous.feature.chart.ui.chart.IndicatorComputePipeline
import com.aandios.nous.feature.chart.ui.chart.IndicatorPaneHeader
import com.aandios.nous.feature.chart.ui.chart.indicatorChartColors
import com.aandios.nous.feature.chart.ui.chart.indicatorPaneRange
import com.aandios.nous.feature.chart.ui.chart.overlayIndicatorRenderer
import com.aandios.nous.feature.chart.ui.chart.paneIndicatorRenderer
import com.aandios.nous.feature.chart.ui.withTheme
import com.aandios.nous.research.indicator.DefaultIndicatorEngine
import com.aandios.nous.research.indicator.IndicatorInstance
import com.aandios.nous.research.indicator.IndicatorOutput
import com.aandios.nous.research.indicator.IndicatorPaneLayout
import com.aandios.nous.research.indicator.IndicatorState
import com.aandios.nous.research.indicator.effectivePaneHeightDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.Cursor
import kotlin.math.roundToInt
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
    initialProviderId: String? = null,
) {
    // Торговые настройки этой панели (trading/paper/show orders/positions)
    // персистятся и восстанавливаются отдельно от других графиков
    LaunchedEffect(workspaceId, panelId, initialProviderId) {
        chartViewModel.attachPanel(workspaceId, panelId)
        // Биржу панели восстанавливаем из персиста (символ/ТФ — из PanelConfig)
        chartViewModel.restoreProvider(initialProviderId)
    }

    ChartWindowContent(
        chartViewModel = chartViewModel,
        modifier = modifier,
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
    initialZoomLevel: Float = 1f,
    onZoomChange: ((Float) -> Unit)? = null,
    workspaceId: String? = null,
    panelId: String? = null,
) {
    val uiState by chartViewModel.state.collectAsState()
    val registry: ProviderRegistry = koinInject()
    val indicatorPresetRepository: IndicatorPresetRepository = koinInject()

    // Feed-backed indicators (liquidations today, funding/OI later) live
    // outside the chart canvas so provider/symbol changes resubscribe once.
    val inputScope = rememberCoroutineScope()
    val liquidationHistory: LiquidationHistorySource = koinInject()
    val indicatorInputSource = remember(inputScope, liquidationHistory) {
        ChartIndicatorInputSource(inputScope, liquidationHistory)
    }
    val liquidationVisibleRange = remember { MutableStateFlow<LongRange?>(null) }
    LaunchedEffect(
        indicatorInputSource,
        uiState.currentSymbol,
        uiState.currentProviderId,
        uiState.currentTimeframe,
    ) {
        val provider = registry.get(uiState.currentProviderId)
        indicatorInputSource.setMarket(
            symbol = uiState.currentSymbol,
            adapter = provider?.liquidation,
            exchange = provider?.config?.displayName,
            timeframe = uiState.currentTimeframe,
        )
    }
    LaunchedEffect(indicatorInputSource, uiState.currentTimeframe) {
        liquidationVisibleRange.collectLatest { range ->
            val visible = range ?: return@collectLatest
            indicatorInputSource.setVisibleRange(
                from = visible.first,
                to = visible.last + Timeframes.millis(uiState.currentTimeframe),
            )
        }
    }
    DisposableEffect(indicatorInputSource) {
        onDispose { indicatorInputSource.close() }
    }

    // Chart trading
    val tradingEnabled by chartViewModel.tradingEnabled.collectAsState()
    val tradingOrders by chartViewModel.openOrders.collectAsState()
    val tradingQuantity by chartViewModel.tradingQuantity.collectAsState()
    val confirmOrders by chartViewModel.confirmOrders.collectAsState()
    val tradingMessages by chartViewModel.tradingMessages.collectAsState()
    val chartOrderType by chartViewModel.chartOrderType.collectAsState()
    val chartReduceOnly by chartViewModel.reduceOnly.collectAsState()
    val chartLeverage by chartViewModel.chartLeverage.collectAsState()
    val chartMarginMode by chartViewModel.chartMarginMode.collectAsState()
    val chartTakeProfit by chartViewModel.takeProfitPrice.collectAsState()
    val chartStopLoss by chartViewModel.stopLossPrice.collectAsState()
    val pendingOrder by chartViewModel.pendingOrder.collectAsState()
    val paperEnabled by chartViewModel.paperEnabled.collectAsState()
    val showOrders by chartViewModel.showOrders.collectAsState()
    val showPositions by chartViewModel.showPositions.collectAsState()
    val tradingPositions by chartViewModel.positions.collectAsState()
    // Свёрнутая (компактная строка) / развёрнутая панель chart trading — персист
    val panelCollapsed by chartViewModel.panelCollapsed.collectAsState()

    // Маржа под ордер и свободные средства — балансы активного адаптера
    // (paper или live): для USDT-M — USDT, для COIN-M — монета маржи.
    val chartBalances by chartViewModel.balances.collectAsState()
    val marginSymbolInfo = uiState.currentSymbolInfo
    val inverseMargin = marginSymbolInfo?.isInverse == true
    val contractSizeMargin = marginSymbolInfo?.contractSize ?: 1.0
    val leverageForInfo = (chartLeverage ?: 1).coerceAtLeast(1)
    val marginCurrency = if (inverseMargin) {
        marginSymbolInfo?.baseAsset?.uppercase() ?: "USDT"
    } else {
        "USDT"
    }
    val availableMargin = chartBalances
        .firstOrNull { it.currency.equals(marginCurrency, true) }
        ?.amount?.toDoubleOrNull()
    val marginInfo = availableMargin?.let { available ->
        val freeText = "${uiState.currentSymbolFormatter.formatVolumeFull(available)} $marginCurrency"
        val price = chartViewModel.chartLastPrice()
        val qty = tradingQuantity ?: uiState.currentSymbolInfo?.minQty
        if (price != null && price > 0.0 && qty != null && qty > 0.0) {
            if (inverseMargin) {
                // COIN-M: qty в контрактах, маржа = qty * contractSize / leverage (USD)
                val marginUsd = qty * contractSizeMargin / leverageForInfo
                val maxContracts = available * leverageForInfo * price / contractSizeMargin
                "Margin ≈ ${uiState.currentSymbolFormatter.formatVolumeFull(marginUsd)} USDT · " +
                        "Free $freeText · Max ${kotlin.math.floor(maxContracts)} cont (${leverageForInfo}x)"
            } else {
                val margin = price * qty / leverageForInfo
                val maxNotional = available * leverageForInfo
                "Margin ≈ ${uiState.currentSymbolFormatter.formatVolumeFull(margin)} USDT · " +
                        "Free $freeText · Max ${uiState.currentSymbolFormatter.formatVolumeFull(maxNotional)} USDT (${leverageForInfo}x)"
            }
        } else {
            "Free $freeText (${leverageForInfo}x)"
        }
    }

    // PnL текущей позиции: % от изменения цены, изменение цены, USDT (живая цена)
    val pnlPosition = tradingPositions.firstOrNull()
    val pnlInfo: Pair<String, Boolean>? = pnlPosition?.let { p ->
        val mark = chartViewModel.chartLastPrice() ?: p.markPrice
        if (mark <= 0.0 || p.avgPrice <= 0.0) return@let null
        val info = uiState.currentSymbolInfo
        val inverse = info?.isInverse == true
        val cs = info?.contractSize ?: 1.0
        val dir = if (p.side == TradeSide.BUY) 1.0 else -1.0
        val pnl = if (inverse) {
            p.quantity * cs * (mark - p.avgPrice) / p.avgPrice * dir
        } else {
            (mark - p.avgPrice) * p.quantity * dir
        }
        val pct = (mark - p.avgPrice) * dir / p.avgPrice * 100.0
        val deltaText = if (inverse) {
            val coinDelta = p.quantity * cs * (1.0 / p.avgPrice - 1.0 / mark) * dir
            plainDecimalString(kotlin.math.round(coinDelta * 1e8) / 1e8)
        } else {
            uiState.currentSymbolFormatter.formatPrice((mark - p.avgPrice) * dir)
        }
        val sign = if (pnl >= 0) "+" else ""
        val text = "PnL $sign${String.format(java.util.Locale.US, "%.2f", pct)}%  $sign$deltaText  " +
                "$sign${String.format(java.util.Locale.US, "%.2f", pnl)} USDT"
        text to (pnl >= 0)
    }
    LaunchedEffect(uiState.currentSymbol, uiState.currentProviderId) {
        // Ордера видны всегда (и при выключенном Trading — только просмотр)
        chartViewModel.refreshOpenOrders()
    }

    val designTokens = LocalTerminalDesignTokens.current
    val chartConfig = remember(
        uiState.fpAggregation,
        uiState.fpProfileScale,
        uiState.fpRingWidth,
        uiState.fpRingDiameter,
        uiState.currentSymbolFormatter,
        designTokens,
    ) {
        DefaultChartConfig.withTheme(designTokens).let { themed ->
            themed.copy(
                footprintConfig = themed.footprintConfig.copy(
                    aggregationLevel = uiState.fpAggregation,
                    tickSize = uiState.currentSymbolFormatter.tickSize,
                    profileScale = uiState.fpProfileScale,
                    ringWidth = uiState.fpRingWidth,
                    ringDiameter = uiState.fpRingDiameter,
                ),
                priceFormatter = uiState.currentSymbolFormatter
            )
        }
    }

    val indicatorColors = remember(designTokens) { indicatorChartColors(designTokens.colors) }

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

    // Имя биржи для водяного знака (только реально зарегистрированные
    // провайдеры — ProviderRegistry)
    val currentExchange = registry.displayName(uiState.currentProviderId)
        .ifEmpty { registry.first()?.config?.displayName.orEmpty() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Верхняя панель — только для standalone-графика (без тайтла панели):
        // в воркспейсе эти контролы переехали в верхний тайтл (PanelHeader).
        // Не зависит от состояния загрузки графика: символ/ТФ/режим можно
        // менять даже при Loading/Error.
        if (workspaceId == null) {
            TerminalPanelHeader {
                ChartTitleToolbar(
                    chartViewModel = chartViewModel,
                    settingsMenu = { dismiss -> ChartSettingsMenu(chartViewModel, dismiss) },
                    indicatorPresetRepository = indicatorPresetRepository,
                )
            }
        }

        // Область графика: Loading/Error — только внутри неё.
        // Панель рисования и водяной знак видны всегда.
        // BoxWithConstraints: ширина нужна, чтобы панель chart trading
        // не вылезала за тайл (в узких тайлах режется соседней панелью).
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(chartConfig.backgroundColor)
        ) {
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
                    color = designTokens.colors.watermark,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = TerminalFontFamily,
                )
                if (symbolInfo != null) {
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = currentExchange,
                            color = designTokens.colors.watermark,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TerminalFontFamily,
                        )
                        contractTypeLabel(symbolInfo)?.let { label ->
                            Text(
                                text = label,
                                color = designTokens.colors.watermark,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = TerminalFontFamily,
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
                            ChartCanvas(
                                candles = state.candles,
                                currentPrice = state.currentPrice,
                                footprintCandles = null,
                                seriesKey = "${uiState.currentProviderId}|${uiState.currentSymbol}|" +
                                        "${uiState.currentTimeframe}|CANDLESTICK",
                                indicators = uiState.indicators,
                                colors = indicatorColors,
                                inputSource = indicatorInputSource,
                                onVisibleTimeRangeChanged = { from, to ->
                                    liquidationVisibleRange.value = from..to
                                },
                                chartConfig = chartConfig,
                                initialZoomLevel = initialZoomLevel,
                                onZoomChange = onZoomChange,
                                drawingHistory = drawingHistory,
                                activeDrawingTool = activeDrawingTool,
                                onActiveDrawingToolChange = { activeDrawingTool = it },
                                tradingOrders = if (showOrders) tradingOrders else emptyList(),
                                tradingPositions = if (showPositions) tradingPositions else emptyList(),
                                symbolBase = if (uiState.currentSymbolInfo?.isInverse == true) {
                                    "cont"
                                } else {
                                    uiState.currentSymbolInfo?.baseAsset ?: uiState.currentSymbol
                                },
                                contractSize = uiState.currentSymbolInfo?.contractSize ?: 1.0,
                                inverse = uiState.currentSymbolInfo?.isInverse == true,
                                tradingEnabled = tradingEnabled,
                                chartViewModel = chartViewModel,
                                onNeedMoreHistory = { chartViewModel.dispatch(ChartIntent.LoadMoreHistory) },
                                hasMoreHistory = uiState.hasMoreHistory,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                ChartMode.FOOTPRINT -> {
                    if (uiState.footprintLoading && uiState.footprintCandles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Loading footprint data...",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp
                            )
                        }
                    } else if (uiState.footprintError != null && uiState.footprintCandles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Footprint data error", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                                Text(
                                    uiState.footprintError!!,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    } else {
                        // Merge completed + live: live overrides same startTime
                        val liveFp = uiState.liveFootprintCandle
                        val completedFp = uiState.footprintCandles
                        val allFp = remember(completedFp, liveFp) {
                            buildList {
                                val liveStart = liveFp?.startTime
                                addAll(completedFp.filter { it.startTime != liveStart })
                                liveFp?.let { add(it) }
                            }
                        }
                        val skeletonCache = remember(uiState.currentSymbol, uiState.currentTimeframe) {
                            mutableMapOf<Long, Pair<FootprintCandle, Candle>>()
                        }
                        val completedSkeletons = remember(completedFp, skeletonCache) {
                            completedFp.map { candle ->
                                val cached = skeletonCache[candle.startTime]
                                if (cached != null && cached.first === candle) {
                                    cached.second
                                } else {
                                    candle.toSkeletonCandle().also {
                                        skeletonCache[candle.startTime] = candle to it
                                    }
                                }
                            }
                        }
                        val liveSkeleton = remember(liveFp) {
                            liveFp?.toSkeletonCandle()
                        }
                        val fpToCandle = remember(completedSkeletons, liveSkeleton) {
                            if (liveSkeleton == null) {
                                completedSkeletons
                            } else {
                                val liveStart = liveSkeleton.timestamp
                                val index = completedSkeletons.indexOfFirst { it.timestamp == liveStart }
                                if (index >= 0) {
                                    completedSkeletons.toMutableList().apply { this[index] = liveSkeleton }
                                } else {
                                    completedSkeletons + liveSkeleton
                                }
                            }
                        }
                        ChartCanvas(
                            candles = fpToCandle,
                            currentPrice = uiState.footprintCurrentPrice ?: fpToCandle.lastOrNull()?.close,
                            footprintCandles = allFp,
                            seriesKey = "${uiState.currentProviderId}|${uiState.currentSymbol}|" +
                                    "${uiState.currentTimeframe}|FOOTPRINT",
                            indicators = uiState.indicators,
                            colors = indicatorColors,
                            inputSource = indicatorInputSource,
                            onVisibleTimeRangeChanged = { from, to ->
                                liquidationVisibleRange.value = from..to
                            },
                            chartConfig = chartConfig,
                            initialZoomLevel = initialZoomLevel,
                            onZoomChange = onZoomChange,
                            drawingHistory = drawingHistory,
                            activeDrawingTool = activeDrawingTool,
                            onActiveDrawingToolChange = { activeDrawingTool = it },
                            tradingOrders = if (showOrders) tradingOrders else emptyList(),
                            tradingPositions = if (showPositions) tradingPositions else emptyList(),
                            symbolBase = if (uiState.currentSymbolInfo?.isInverse == true) {
                                "cont"
                            } else {
                                uiState.currentSymbolInfo?.baseAsset ?: uiState.currentSymbol
                            },
                            contractSize = uiState.currentSymbolInfo?.contractSize ?: 1.0,
                            inverse = uiState.currentSymbolInfo?.isInverse == true,
                            tradingEnabled = tradingEnabled,
                            chartViewModel = chartViewModel,
                            onNeedMoreHistory = {
                                chartViewModel.dispatch(ChartIntent.LoadMoreFootprintHistory)
                            },
                            hasMoreHistory = uiState.hasMoreFootprintHistory,
                            modifier = Modifier.fillMaxSize(),
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
                    // Клики по панели инструментов (мимо кнопок) не проваливаются в чарт
                    .blockChartPointerInput()
            )

            // Панель chart trading (левый нижний угол, вертикальная как
            // DrawingToolPanel): при выключенном Trading скрыта, ордера на
            // графике остаются видны, но только для просмотра.
            if (tradingEnabled) {
                val panelMaxWidth = (maxWidth - 20.dp).coerceAtLeast(160.dp)
                // Развёрнутая — вертикальная узкая панель (стиль DrawingToolPanel);
                // свёрнутая — компактная строка по контенту (нижний край там же)
                val panelWidth = minOf(192.dp, panelMaxWidth)
                ChartTradingPanel(
                    caption = "${uiState.currentSymbol} · ${registry.displayName(uiState.currentProviderId)}",
                    minQty = uiState.currentSymbolInfo?.minQty,
                    quantity = tradingQuantity,
                    orderType = chartOrderType,
                    reduceOnly = chartReduceOnly,
                    leverage = chartLeverage,
                    marginMode = chartMarginMode,
                    takeProfit = chartTakeProfit,
                    stopLoss = chartStopLoss,
                    confirmOrders = confirmOrders,
                    pendingOrder = pendingOrder,
                    bookTicker = registry.get(uiState.currentProviderId)?.bookTicker,
                    symbol = uiState.currentSymbol,
                    formatter = uiState.currentSymbolFormatter,
                    baseAsset = uiState.currentSymbolInfo?.baseAsset,
                    quoteAsset = uiState.currentSymbolInfo?.quoteAsset,
                    marginInfo = marginInfo,
                    pnlText = pnlInfo?.first,
                    pnlUp = pnlInfo?.second ?: true,
                    collapsed = panelCollapsed,
                    onCollapsedChange = { chartViewModel.setPanelCollapsed(it) },
                    onQuantityChanged = { q -> chartViewModel.setTradingQuantity(q) },
                    onOrderTypeChanged = { chartViewModel.setChartOrderType(it) },
                    onReduceOnlyChanged = { chartViewModel.setReduceOnly(it) },
                    onLeverageChanged = { chartViewModel.setChartLeverage(it) },
                    onMarginModeChanged = { chartViewModel.setChartMarginMode(it) },
                    onTakeProfitChanged = { chartViewModel.setTakeProfitPrice(it) },
                    onStopLossChanged = { chartViewModel.setStopLossPrice(it) },
                    onBuy = { chartViewModel.placeMarketOrder(OrderSide.BUY) },
                    onSell = { chartViewModel.placeMarketOrder(OrderSide.SELL) },
                    onBuyLimit = { p -> chartViewModel.placeLimitAtBest(OrderSide.BUY, p) },
                    onSellLimit = { p -> chartViewModel.placeLimitAtBest(OrderSide.SELL, p) },
                    onCancelAll = { chartViewModel.cancelAllChartOrders() },
                    onCloseAll = { chartViewModel.closeAllChartPositions() },
                    onConfirmPending = { chartViewModel.confirmPendingOrder() },
                    onCancelPending = { chartViewModel.cancelPendingOrder() },
                    onConfirmChanged = { c -> chartViewModel.setConfirmOrders(c) },
                    // Быстрый lock: выключает Trading на графике
                    onLock = { chartViewModel.setTradingEnabled(false) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 28.dp)
                        .widthIn(max = if (panelCollapsed) panelMaxWidth else panelWidth),
                )
            }

            // Быстрый unlock: Trading выключен — маленький замок возвращает его
            if (!tradingEnabled) {
                Text(
                    text = "🔓",
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 28.dp)
                        .blockChartPointerInput()
                        .clickable { chartViewModel.setTradingEnabled(true) }
                        .background(designTokens.colors.panelSurface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .border(
                            designTokens.dimensions.borderWidth,
                            designTokens.colors.panelBorder,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }

            // Snackbar-уведомления: вылетают из-под тулбара, стекаются вниз,
            // гаснут через несколько секунд или по крестику
            if (tradingMessages.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .widthIn(max = maxWidth - 24.dp)
                        // Клики по пустым местам snackbar'ов не размещают ордер
                        .blockChartPointerInput(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    tradingMessages.forEach { notification ->
                        ChartNotificationItem(
                            notification = notification,
                            onDismiss = { chartViewModel.dismissTradingMessage(it) },
                        )
                    }
                }
            }
        }
    }
}

private val IndicatorTabsHeight = 20.dp
private val IndicatorHeaderHeight = 18.dp

@Composable
private fun ChartCanvas(
    candles: List<Candle>,
    currentPrice: Float?,
    footprintCandles: List<FootprintCandle>?,
    seriesKey: String,
    indicators: IndicatorState,
    colors: IndicatorChartColors,
    inputSource: ChartIndicatorInputSource,
    onVisibleTimeRangeChanged: (Long, Long) -> Unit,
    chartConfig: ChartConfig,
    initialZoomLevel: Float,
    onZoomChange: ((Float) -> Unit)?,
    drawingHistory: DrawingHistory,
    activeDrawingTool: DrawingToolType,
    onActiveDrawingToolChange: (DrawingToolType) -> Unit,
    tradingOrders: List<com.aandios.nous.api.market.model.trading.Order>,
    tradingPositions: List<com.aandios.nous.api.market.model.trading.Position>,
    symbolBase: String?,
    contractSize: Double,
    inverse: Boolean,
    tradingEnabled: Boolean,
    chartViewModel: ChartViewModel,
    onNeedMoreHistory: () -> Unit,
    hasMoreHistory: Boolean,
    modifier: Modifier = Modifier,
) {
    val visuals = rememberIndicatorRenderers(
        candles = candles,
        indicators = indicators,
        seriesKey = seriesKey,
        colors = colors,
        inputSource = inputSource,
    )
    val activePaneId = indicators.activePaneId
        ?.takeIf { id -> indicators.panes.any { it.id == id } }
        ?: indicators.panes.firstOrNull()?.id
    val visiblePanes = when (indicators.paneLayout) {
        IndicatorPaneLayout.TABS -> visuals.panes.filter { it.paneId == activePaneId }
        IndicatorPaneLayout.STACKED -> visuals.panes
    }
    val paneHeights = visiblePanes.map { visual ->
        indicators.effectivePaneHeightDp(visual.paneId)
    }

    var chartLayout by remember { mutableStateOf<ChartLayout?>(null) }
    val density = LocalDensity.current

    Box(modifier) {
        CandleStickChart(
            candles = candles,
            currentPrice = currentPrice,
            config = chartConfig,
            overlayRenderers = visuals.overlays,
            indicatorRenderers = visiblePanes.map { it.renderer },
            indicatorHeightsDp = paneHeights.map { it.dp },
            indicatorValueRanges = visiblePanes.map { it.valueRange },
            indicatorTabsHeightDp = IndicatorTabsHeight,
            indicatorHeaderHeightDp = IndicatorHeaderHeight,
            onLayoutChanged = { chartLayout = it },
            onVisibleTimeRangeChanged = onVisibleTimeRangeChanged,
            footprintCandles = footprintCandles,
            onNeedMoreHistory = onNeedMoreHistory,
            hasMoreHistory = hasMoreHistory,
            drawingHistory = drawingHistory,
            activeDrawingTool = activeDrawingTool,
            onActiveDrawingToolChange = onActiveDrawingToolChange,
            modifier = Modifier.fillMaxSize(),
            initialZoomLevel = initialZoomLevel,
            onZoomChange = onZoomChange,
            tradingOrders = tradingOrders,
            tradingPositions = tradingPositions,
            symbolBase = symbolBase,
            contractSize = contractSize,
            inverse = inverse,
            onChartTradingClick = if (tradingEnabled) {
                { price -> chartViewModel.placeChartOrder(price) }
            } else null,
            // Trading off — бейджи только для просмотра
            onCancelTradingOrder = if (tradingEnabled) {
                { chartViewModel.cancelChartOrder(it.orderId) }
            } else null,
            onMoveTradingOrder = if (tradingEnabled) {
                { order, price -> chartViewModel.moveChartOrder(order, price) }
            } else null,
            onResizeTradingOrder = if (tradingEnabled) {
                { order, qty -> chartViewModel.resizeChartOrder(order, qty) }
            } else null,
            onCloseTradingPosition = if (tradingEnabled) {
                { position -> chartViewModel.closeChartPosition(position) }
            } else null,
        )

        val layout = chartLayout
        if (layout != null && visiblePanes.isNotEmpty()) {
            layout.indicatorTabsArea?.let { area ->
                IndicatorPaneTabs(
                    state = indicators,
                    onSelectPane = {
                        chartViewModel.dispatch(ChartIntent.SelectIndicatorPane(it))
                    },
                    onMovePane = { paneId, targetIndex ->
                        chartViewModel.dispatch(ChartIntent.MoveIndicatorPane(paneId, targetIndex))
                    },
                    modifier = Modifier
                        .chartArea(area, density)
                        .blockChartPointerInput(),
                )
            }
            visiblePanes.forEachIndexed { index, visual ->
                val pane = indicators.panes.firstOrNull { it.id == visual.paneId } ?: return@forEachIndexed
                layout.indicatorHeaderAreas.getOrNull(index)?.let { area ->
                    IndicatorPaneHeader(
                        outputs = visual.outputs,
                        instances = pane.indicators,
                        colors = colors,
                        modifier = Modifier
                            .chartArea(area, density)
                            .blockChartPointerInput(),
                    )
                }
            }
            visiblePanes.forEachIndexed { index, visual ->
                if (indicators.panes.none { it.id == visual.paneId }) return@forEachIndexed
                val header = layout.indicatorHeaderAreas.getOrNull(index) ?: return@forEachIndexed
                val halfHit = with(density) { 6.dp.toPx() }
                IndicatorPaneResizeBoundary(
                    paneId = visual.paneId,
                    heightDp = paneHeights[index],
                    onHeightChange = { height ->
                        chartViewModel.dispatch(
                            ChartIntent.SetIndicatorPaneHeight(
                                paneId = visual.paneId,
                                heightDp = height,
                                persist = false,
                            ),
                        )
                    },
                    onHeightCommit = {
                        val height = chartViewModel.state.value.indicators
                            .effectivePaneHeightDp(visual.paneId)
                        chartViewModel.dispatch(
                            ChartIntent.SetIndicatorPaneHeight(
                                paneId = visual.paneId,
                                heightDp = height,
                                persist = true,
                            ),
                        )
                    },
                    modifier = Modifier.chartArea(
                        Rect(
                            left = header.left,
                            top = header.top - halfHit,
                            right = header.right,
                            bottom = header.top + halfHit,
                        ),
                        density,
                    ),
                )
            }
        }
    }
}

@Composable
private fun IndicatorPaneResizeBoundary(
    paneId: String,
    heightDp: Float,
    onHeightChange: (Float) -> Unit,
    onHeightCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    val currentHeight by rememberUpdatedState(heightDp)
    val currentOnChange by rememberUpdatedState(onHeightChange)
    val currentOnCommit by rememberUpdatedState(onHeightCommit)
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging
    val resizeCursor = remember {
        PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .pointerHoverIcon(resizeCursor)
            .hoverable(interactionSource)
            .pointerInput(paneId) {
                var startHeight = currentHeight
                var totalDy = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        dragging = true
                        startHeight = currentHeight
                        totalDy = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        currentOnCommit()
                    },
                    onDragEnd = {
                        dragging = false
                        currentOnCommit()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDy += dragAmount
                        currentOnChange(startHeight - totalDy / density)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(tokens.colors.accent.copy(alpha = 0.5f)),
            )
        }
    }
}

private fun Modifier.chartArea(area: Rect, density: Density): Modifier = this
    .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
    .size(
        width = with(density) { area.width.toDp() },
        height = with(density) { area.height.toDp() },
    )

/** Snackbar чарта: слайд из-под тулбара, таймаут ~4.5с, крестик для закрытия. */
@Composable
private fun ChartNotificationItem(
    notification: ChartNotification,
    onDismiss: (Long) -> Unit,
) {
    val designTokens = LocalTerminalDesignTokens.current
    var visible by remember(notification.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(notification.id) {
        visible = true
        delay(4500)
        visible = false
        delay(200)
        onDismiss(notification.id)
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    designTokens.colors.panelHeaderSurface.copy(alpha = 0.95f),
                    RoundedCornerShape(designTokens.cornerRadii.card),
                )
                .border(
                    designTokens.dimensions.borderWidth,
                    designTokens.colors.panelBorder,
                    RoundedCornerShape(designTokens.cornerRadii.card),
                )
                .padding(
                    start = designTokens.spacing.md,
                    end = designTokens.spacing.xxs,
                    top = designTokens.spacing.xs,
                    bottom = designTokens.spacing.xs,
                ),
        ) {
            Text(
                text = notification.text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontFamily = TerminalFontFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "✕",
                color = designTokens.colors.label,
                fontSize = 11.sp,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
                modifier = Modifier
                    .clickable {
                        visible = false
                        scope.launch {
                            delay(200)
                            onDismiss(notification.id)
                        }
                    }
                    .padding(horizontal = 6.dp),
            )
        }
    }
}

private typealias ChartIndicatorRenderer =
        DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit

private data class ChartPaneVisual(
    val paneId: String,
    val outputs: List<IndicatorOutput>,
    val valueRange: Pair<Float, Float>?,
    val renderer: ChartIndicatorRenderer,
)

private data class ChartIndicatorVisuals(
    val overlays: List<ChartIndicatorRenderer>,
    val panes: List<ChartPaneVisual>,
)

@Composable
private fun rememberIndicatorRenderers(
    candles: List<Candle>,
    indicators: IndicatorState,
    seriesKey: String,
    colors: IndicatorChartColors,
    inputSource: ChartIndicatorInputSource,
): ChartIndicatorVisuals {
    val instances = remember(indicators) {
        buildList<IndicatorInstance> {
            addAll(indicators.overlays)
            indicators.panes.forEach { addAll(it.indicators) }
        }
    }
    val engine = remember(inputSource) { DefaultIndicatorEngine(inputSource) }
    val scope = rememberCoroutineScope()
    val pipeline = remember(engine, scope, seriesKey) { IndicatorComputePipeline(engine, scope) }
    DisposableEffect(pipeline) {
        onDispose { pipeline.close() }
    }
    LaunchedEffect(pipeline, inputSource) {
        inputSource.updates().collect { pipeline.refresh() }
    }
    LaunchedEffect(
        pipeline,
        candles.size,
        candles.firstOrNull()?.timestamp,
        candles.lastOrNull(),
        instances,
    ) {
        pipeline.submit(candles, instances)
    }
    val outputs by pipeline.outputs.collectAsState()

    val overlayOutputs = indicators.overlays.mapNotNull { outputs[it.id] }
    val overlayRenderers = remember(overlayOutputs, colors) {
        if (overlayOutputs.isEmpty()) {
            emptyList()
        } else {
            listOf(overlayIndicatorRenderer(overlayOutputs, colors))
        }
    }
    val paneVisuals = remember(indicators.panes, outputs, colors) {
        indicators.panes.map { pane ->
            val paneOutputs = pane.indicators.mapNotNull { outputs[it.id] }
            ChartPaneVisual(
                paneId = pane.id,
                outputs = paneOutputs,
                valueRange = paneOutputs.takeIf { it.isNotEmpty() }?.let(::indicatorPaneRange),
                renderer = paneIndicatorRenderer(
                    outputs = paneOutputs,
                    colors = colors,
                ),
            )
        }
    }
    return ChartIndicatorVisuals(overlayRenderers, paneVisuals)
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    stopKoin()
    initKoinForPreview()

    SwingWindow(
        onCloseRequest = ::exitApplication,
        title = "Nous Platform • Chart Preview",
        state = rememberWindowStateWithBounds(initialSize = DpSize(800.dp, 600.dp)),
        // До показа окна: фон AWT-фрейма — без белых вспышек при ресайзе.
        init = { w -> applyWindowBackground(w) },
    ) {
        // Тёмный заголовок окна (Windows, DWM); no-op на других ОС.
        LaunchedEffect(Unit) { applyWindowsDarkTitleBar(window) }
        KoinContext {
            TradingTerminalTheme {
                ChartWindow()
            }
        }
    }
}