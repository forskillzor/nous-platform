/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.v2.rememberWindowStateWithBounds
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.api.market.paper.PaperTrading
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.ui.window.applyWindowDarkBackground
import com.aandios.nous.core.ui.window.applyWindowsDarkTitleBar
import com.aandios.nous.feature.chart.di.initKoinForPreview
import com.aandios.nous.feature.chart.indicator.LiquidationViewModel
import com.aandios.nous.feature.chart.model.PriceRange
import com.aandios.nous.feature.chart.model.toSkeletonCandle
import com.aandios.nous.feature.chart.tools.DrawingHistory
import com.aandios.nous.feature.chart.tools.DrawingRepository
import com.aandios.nous.feature.chart.tools.DrawingToolType
import com.aandios.nous.feature.chart.ui.chart.CandleStickChart
import com.aandios.nous.feature.chart.ui.chart.drawLiquidationHistogram
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
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
) {
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
    val paperEnabled by PaperTrading.enabledFlow.collectAsState()
    val showOrders by chartViewModel.showOrders.collectAsState()
    val showPositions by chartViewModel.showPositions.collectAsState()
    val tradingPositions by chartViewModel.positions.collectAsState()

    // Paper: ориентир по марже и свободному балансу с учётом плеча
    val paperBalances by PaperTrading.adapter.balancesFlow.collectAsState()
    val paperAvailable = paperBalances.firstOrNull { it.currency == "USDT" }?.amount?.toDoubleOrNull()
    val leverageForInfo = (chartLeverage ?: 1).coerceAtLeast(1)
    val marginInfo = if (paperEnabled && paperAvailable != null) {
        val maxNotional = paperAvailable * leverageForInfo
        val base = "Free ${uiState.currentSymbolFormatter.formatVolumeFull(paperAvailable)} · " +
            "Max ${uiState.currentSymbolFormatter.formatVolumeFull(maxNotional)} USDT (${leverageForInfo}x)"
        val price = chartViewModel.chartLastPrice()
        val qty = tradingQuantity ?: uiState.currentSymbolInfo?.minQty
        if (price != null && qty != null && price > 0 && qty > 0) {
            "Margin ≈ ${uiState.currentSymbolFormatter.formatVolumeFull(price * qty / leverageForInfo)} · $base"
        } else base
    } else null

    // PnL текущей позиции: %, тики, USDT — считается от живой цены графика
    val pnlPosition = tradingPositions.firstOrNull()
    val pnlInfo: Pair<String, Boolean>? = pnlPosition?.let { p ->
        val mark = chartViewModel.chartLastPrice() ?: p.markPrice
        if (mark <= 0.0 || p.avgPrice <= 0.0) return@let null
        val lev = (p.leverage ?: 1).coerceAtLeast(1)
        val margin = p.avgPrice * p.quantity / lev
        val dir = if (p.side == TradeSide.BUY) 1.0 else -1.0
        val pnl = (mark - p.avgPrice) * p.quantity * dir
        val pct = if (margin > 0) pnl / margin * 100 else 0.0
        val tick = uiState.currentSymbolFormatter.tickSize
        val ticks = if (tick > 0) (mark - p.avgPrice) * dir / tick else 0.0
        val sign = if (pnl >= 0) "+" else ""
        val text = "PnL $sign${String.format(java.util.Locale.US, "%.2f", pct)}%  " +
            "$sign${ticks.roundToLong()}t  " +
            "$sign${String.format(java.util.Locale.US, "%.2f", pnl)} USDT"
        text to (pnl >= 0)
    }
    // Свёрнутая (компактная строка) / развёрнутая панель chart trading
    var panelCollapsed by remember { mutableStateOf(false) }

    // Лучшие bid/ask для лимиток «по лучшей цене» (Buy Limit / Sell Limit)
    var bestBid by remember { mutableStateOf<Double?>(null) }
    var bestAsk by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(uiState.currentProviderId, uiState.currentSymbol) {
        bestBid = null
        bestAsk = null
        val ticker = registry.get(uiState.currentProviderId)?.bookTicker ?: return@LaunchedEffect
        ticker.subscribeToBookTicker(uiState.currentSymbol).collect { tick ->
            bestBid = tick.bestBid.takeIf { it > 0 }
            bestAsk = tick.bestAsk.takeIf { it > 0 }
        }
    }

    LaunchedEffect(uiState.currentSymbol, uiState.currentProviderId) {
        // Ордера видны всегда (и при выключенном Trading — только просмотр)
        chartViewModel.refreshOpenOrders()
    }

    // Ликвидации — от адаптера АКТИВНОГО провайдера (у MEXC его нет → null)
    val liquidationViewModel = remember(registry) {
        LiquidationViewModel(registry.get(uiState.currentProviderId)?.liquidation)
    }
    LaunchedEffect(uiState.currentProviderId) {
        liquidationViewModel.setAdapter(registry.get(uiState.currentProviderId)?.liquidation)
    }

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
        // Список бирж — только реально зарегистрированные провайдеры (ProviderRegistry).
        val exchanges = registry.providers.map { it.config.displayName }
        val currentExchange = registry.displayName(uiState.currentProviderId)
            .ifEmpty { registry.first()?.config?.displayName.orEmpty() }

        ChartToolbar(
            currentSymbol = uiState.currentSymbol,
            currentTimeframe = uiState.currentTimeframe,
            availableSymbols = uiState.symbols,
            onSymbolChange = { chartViewModel.dispatch(ChartIntent.SelectSymbol(it)) },
            onTimeframeChange = { chartViewModel.dispatch(ChartIntent.SelectTimeframe(it)) },
            exchanges = exchanges,
            currentExchange = currentExchange,
            onExchangeChange = { name ->
                registry.idByDisplayName(name)?.let { id ->
                    chartViewModel.dispatch(ChartIntent.SelectProvider(id))
                }
            },
            chartMode = uiState.chartMode,
            onChartModeChange = { chartViewModel.dispatch(ChartIntent.SelectChartMode(it)) },
            symbolsWithFootprint = uiState.symbolsWithFootprint,
            fpAggregation = uiState.fpAggregation,
            onFpAggregationChange = { chartViewModel.dispatch(ChartIntent.SetFpAggregation(it)) },
            tradingEnabled = tradingEnabled,
            onTradingToggle = { chartViewModel.setTradingEnabled(it) },
            paperEnabled = paperEnabled,
            onPaperToggle = { chartViewModel.setPaperEnabled(it) },
            showOrders = showOrders,
            onShowOrdersToggle = { chartViewModel.setShowOrders(it) },
            showPositions = showPositions,
            onShowPositionsToggle = { chartViewModel.setShowPositions(it) },
            modifier = Modifier.padding(8.dp)
        )

        // Область графика: Loading/Error — только внутри неё.
        // Панель рисования и водяной знак видны всегда.
        // BoxWithConstraints: ширина нужна, чтобы панель chart trading
        // не вылезала за тайл (в узких тайлах режется соседней панелью).
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                            text = currentExchange,
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
                                tradingOrders = if (showOrders) tradingOrders else emptyList(),
                                tradingPositions = if (showPositions) tradingPositions else emptyList(),
                                symbolBase = uiState.currentSymbolInfo?.baseAsset ?: uiState.currentSymbol,
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
                            tradingOrders = if (showOrders) tradingOrders else emptyList(),
                            tradingPositions = if (showPositions) tradingPositions else emptyList(),
                            symbolBase = uiState.currentSymbolInfo?.baseAsset ?: uiState.currentSymbol,
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
                    bestBid = bestBid,
                    bestAsk = bestAsk,
                    formatter = uiState.currentSymbolFormatter,
                    marginInfo = marginInfo,
                    pnlText = pnlInfo?.first,
                    pnlUp = pnlInfo?.second ?: true,
                    collapsed = panelCollapsed,
                    onCollapsedChange = { panelCollapsed = it },
                    onQuantityChanged = { q -> chartViewModel.setTradingQuantity(q) },
                    onOrderTypeChanged = { chartViewModel.setChartOrderType(it) },
                    onReduceOnlyChanged = { chartViewModel.setReduceOnly(it) },
                    onLeverageChanged = { chartViewModel.setChartLeverage(it) },
                    onMarginModeChanged = { chartViewModel.setChartMarginMode(it) },
                    onTakeProfitChanged = { chartViewModel.setTakeProfitPrice(it) },
                    onStopLossChanged = { chartViewModel.setStopLossPrice(it) },
                    onBuy = { chartViewModel.placeMarketOrder(OrderSide.BUY) },
                    onSell = { chartViewModel.placeMarketOrder(OrderSide.SELL) },
                    onBuyLimit = { bestBid?.let { p -> chartViewModel.placeLimitAtBest(OrderSide.BUY, p) } },
                    onSellLimit = { bestAsk?.let { p -> chartViewModel.placeLimitAtBest(OrderSide.SELL, p) } },
                    onCancelAll = { chartViewModel.cancelAllChartOrders() },
                    onCloseAll = { chartViewModel.closeAllChartPositions() },
                    onConfirmPending = { chartViewModel.confirmPendingOrder() },
                    onCancelPending = { chartViewModel.cancelPendingOrder() },
                    onConfirmChanged = { c -> chartViewModel.setConfirmOrders(c) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 28.dp)
                        .widthIn(max = if (panelCollapsed) panelMaxWidth else panelWidth),
                )
            }

            // Snackbar-уведомления: вылетают из-под тулбара, стекаются вниз,
            // гаснут через несколько секунд или по крестику
            if (tradingMessages.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .widthIn(max = maxWidth - 24.dp),
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

/** Snackbar чарта: слайд из-под тулбара, таймаут ~4.5с, крестик для закрытия. */
@Composable
private fun ChartNotificationItem(
    notification: ChartNotification,
    onDismiss: (Long) -> Unit,
) {
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
                .background(Color(0xFF14181F).copy(alpha = 0.95f), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF3A4550), RoundedCornerShape(4.dp))
                .padding(start = 8.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
        ) {
            Text(
                text = notification.text,
                color = Color(0xFFD5DBE1),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "✕",
                color = Color(0xFF8A97A5),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
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
        // Тёмный заголовок окна (Windows, DWM); no-op на других ОС.
        LaunchedEffect(Unit) { applyWindowsDarkTitleBar(window) }
        KoinContext {
            TradingTerminalTheme {
                ChartWindow()
            }
        }
    }
}