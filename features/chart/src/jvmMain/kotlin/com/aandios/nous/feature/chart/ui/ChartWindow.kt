/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui
import com.aandios.nous.core.ui.theme.TerminalFontFamily

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
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.core.ui.component.TerminalPanelHeader
import com.aandios.nous.core.ui.format.plainDecimalString
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.ui.window.applyWindowBackground
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
import com.aandios.nous.feature.chart.ui.withTheme
import kotlinx.coroutines.delay
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
    LaunchedEffect(workspaceId, panelId) {
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

    val designTokens = LocalTerminalDesignTokens.current
    val chartConfig = remember(uiState.fpAggregation, uiState.currentSymbolFormatter, designTokens) {
        DefaultChartConfig.withTheme(designTokens).let { themed ->
            themed.copy(
                footprintConfig = themed.footprintConfig.copy(
                    aggregationLevel = uiState.fpAggregation,
                    tickSize = uiState.currentSymbolFormatter.tickSize
                ),
                priceFormatter = uiState.currentSymbolFormatter
            )
        }
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
    val indicatorRenderers = remember(liqOrders, designTokens.colors.bullish, designTokens.colors.bearish) {
        listOf<DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit>(
            { area, candles, _, scroll, zoom ->
                drawLiquidationHistogram(
                    area = area,
                    candles = candles,
                    orders = liqOrders,
                    scrollOffset = scroll,
                    zoomLevel = zoom,
                    longColor = designTokens.colors.bullish,
                    shortColor = designTokens.colors.bearish,
                )
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
                    registry = registry,
                    settingsMenu = { dismiss -> ChartSettingsMenu(chartViewModel, dismiss) },
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
                            CandleStickChart(
                                candles = state.candles,
                                currentPrice = state.currentPrice,
                                config = chartConfig,
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
                                symbolBase = if (uiState.currentSymbolInfo?.isInverse == true) {
                                    "cont"
                                } else {
                                    uiState.currentSymbolInfo?.baseAsset ?: uiState.currentSymbol
                                },
                                contractSize = uiState.currentSymbolInfo?.contractSize ?: 1.0,
                                inverse = uiState.currentSymbolInfo?.isInverse == true,
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
                            symbolBase = if (uiState.currentSymbolInfo?.isInverse == true) {
                        "cont"
                    } else {
                        uiState.currentSymbolInfo?.baseAsset ?: uiState.currentSymbol
                    },
                    contractSize = uiState.currentSymbolInfo?.contractSize ?: 1.0,
                    inverse = uiState.currentSymbolInfo?.isInverse == true,
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
                    bestBid = bestBid,
                    bestAsk = bestAsk,
                    formatter = uiState.currentSymbolFormatter,
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
                    onBuyLimit = { bestBid?.let { p -> chartViewModel.placeLimitAtBest(OrderSide.BUY, p) } },
                    onSellLimit = { bestAsk?.let { p -> chartViewModel.placeLimitAtBest(OrderSide.SELL, p) } },
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