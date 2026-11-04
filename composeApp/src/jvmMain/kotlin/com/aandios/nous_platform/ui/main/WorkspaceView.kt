/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.ui.component.SymbolExplorerDropdown
import com.aandios.nous.core.ui.component.TerminalMenuItem
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.workspace.LayoutRenderer
import com.aandios.nous.core.workspace.*
import com.aandios.nous.core.workspace.viewmodel.WorkspaceViewModel
import com.aandios.nous.feature.chart.ui.ChartIntent
import com.aandios.nous.feature.chart.ui.ChartMode
import com.aandios.nous.feature.chart.ui.ChartSettingsMenu
import com.aandios.nous.feature.chart.ui.ChartTitleToolbar
import com.aandios.nous.feature.chart.ui.ChartViewModel
import com.aandios.nous.feature.chart.ui.ChartWindow
import com.aandios.nous.feature.dom.domain.TradingSymbol
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import com.aandios.nous.feature.dom.ui.DomViewModel
import com.aandios.nous.feature.dom.ui.DomWindow
import com.aandios.nous.feature.dom.ui.DomRecommendedWidth
import com.aandios.nous.feature.trades.ui.SizeFilter
import com.aandios.nous.feature.trades.ui.TradesViewModel
import com.aandios.nous.feature.trades.ui.TradesWidthMenuContent
import com.aandios.nous.feature.trades.ui.TradesWindow
import com.aandios.nous.feature.trading.ui.TradingViewModel
import com.aandios.nous.feature.trading.ui.TradingWindow
import com.aandios.nous.feature.trades.ui.TradesRecommendedWidth
import com.aandios.nous.feature.trading.ui.TradingRecommendedWidth
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Рендер одного workspace: дерево панелей (LayoutRenderer) + per-panel ViewModels.
 * Общий для всех окон терминала.
 */
@Composable
fun WorkspaceView(
    ws: WorkspaceViewModel,
    workspaceRepo: WorkspaceRepository,
    modifier: Modifier = Modifier,
    onManageWatchlist: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val providerRegistry: ProviderRegistry = koinInject()

    var panelConfigs by remember(ws.config.id) { mutableStateOf(ws.config.panels.associateBy { it.id }) }
    var layoutState by remember(ws.config.id) { mutableStateOf(ws.config.layout) }
    // Ширина Trades-панелей (настройка шестерёнкой, персистится в VM)
    val tradesWidths = remember { mutableStateMapOf<String, androidx.compose.ui.unit.Dp>() }

    // Undo/redo сплитов (Ctrl+Z / Ctrl+Shift+Z) — история на workspace
    val history = remember(ws.config.id) { LayoutHistory() }

    fun snapshot() = LayoutSnapshot(layout = layoutState.deepCopy(), panels = panelConfigs.toMap())

    fun persistConfig() {
        val config = ws.config.copy(
            layout = layoutState,
            panels = panelConfigs.values.toList()
        )
        ws.updateConfig(config)
        scope.launch { workspaceRepo.update(config) }
    }

    fun applySnapshot(snap: LayoutSnapshot) {
        layoutState = snap.layout
        panelConfigs = snap.panels
        persistConfig()
    }

    fun updatePanelConfig(updated: PanelConfig) {
        panelConfigs = panelConfigs + (updated.id to updated)
        persistConfig()
    }

    // Per-panel биржа: зеркалим выбор в конфиг этой панели (без общего ProviderRef)
    fun updatePanelProvider(panel: PanelConfig, providerId: String) {
        val name = providerRegistry.displayName(providerId)
        if (panel.providerId == providerId && panel.providerName == name) return
        updatePanelConfig(panel.copy(providerId = providerId, providerName = name))
    }

    // Внешние обновления конфига («Apply symbol to current workspace»): символы и
    // провайдеры панелей меняются вне WorkspaceView — подтягиваем их в локальный
    // state; layout и panel-настройки остаются локальными.
    LaunchedEffect(ws.config.id) {
        snapshotFlow { ws.config }.collect { cfg ->
            val incoming = cfg.panels.associateBy { it.id }
            var next = panelConfigs
            incoming.forEach { (id, remote) ->
                val local = next[id] ?: return@forEach
                val merged = applyExternalPanelState(local, remote)
                if (merged !== local) next = next + (id to merged)
            }
            if (next !== panelConfigs) {
                panelConfigs = next
                persistConfig()
            }
        }
    }

    fun panelVmKey(pc: PanelConfig): String {
        val suffix = when (pc.type) {
            PanelType.CHART -> "_chart"
            PanelType.DOM -> "_dom"
            PanelType.TRADES -> "_trades"
            PanelType.TRADING -> "_trading"
        }
        return "${pc.id}$suffix"
    }

    fun disposePanelVm(pc: PanelConfig) {
        (ws.liveViewModels.remove(panelVmKey(pc)) as? com.aandios.nous.core.Disposable)?.dispose()
    }

    fun closePanel(panelId: String) {
        val newLayout = LayoutEngine.removePanel(layoutState, panelId)
        if (newLayout != null) {
            history.push(snapshot())
            layoutState = newLayout
            val removedPc = panelConfigs[panelId]
            panelConfigs = panelConfigs - panelId
            removedPc?.let { disposePanelVm(it) }
            persistConfig()
        }
    }

    // Корень — фокусируемый, чтобы ловить Ctrl+Z / Ctrl+Shift+Z (undo/redo сплитов).
    // Если истории нет — не потребляем событие: оно уйдёт к undo рисунков графика.
    val focusInteraction = remember { MutableInteractionSource() }
    val designTokens = LocalTerminalDesignTokens.current

    Box(
        modifier = modifier
            .background(designTokens.colors.workspaceBackground)
            .clickable(
                interactionSource = focusInteraction,
                indication = null
            ) { /* no-op: focusable for onKeyEvent */ }
            .onKeyEvent { event ->
                when {
                    event.key == Key.Z && event.isCtrlPressed &&
                        event.type == KeyEventType.KeyDown -> {
                        if (history.canUndo) {
                            val prev = history.undo(snapshot())
                            if (prev != null) applySnapshot(prev)
                            true
                        } else false
                    }
                    event.key == Key.Z && event.isCtrlPressed && event.isShiftPressed &&
                        event.type == KeyEventType.KeyDown -> {
                        if (history.canRedo) {
                            val next = history.redo(snapshot())
                            if (next != null) applySnapshot(next)
                            true
                        } else false
                    }
                    else -> false
                }
            }
    ) {
        LayoutRenderer(
            node = layoutState,
            panels = panelConfigs,
            modifier = Modifier.fillMaxSize(),
            // DOM/Trades — фиксированная ширина (рекомендованная самим виджетом),
            // CHART и вложенные сплиты делят остаток.
            fixedPanelWidths = panelConfigs.mapNotNull { (id, pc) ->
                when (pc.type) {
                    PanelType.DOM -> id to DomRecommendedWidth
                    PanelType.TRADES -> id to (tradesWidths[id] ?: TradesRecommendedWidth)
                    PanelType.CHART -> null
                    PanelType.TRADING -> id to TradingRecommendedWidth
                }
            }.toMap(),
            onRatioChange = { persistConfig() },
            onRatioChangeStart = { history.push(snapshot()) },
            onClosePanel = { panelId -> closePanel(panelId) },
            onSplitPanel = { panelId, direction, newType ->
                val newPanelId = "panel-${generateId()}"
                history.push(snapshot())
                val newLayout = LayoutEngine.split(layoutState, panelId, direction, newPanelId)
                layoutState = newLayout
                val newConfig = PanelConfig(
                    id = newPanelId,
                    type = newType,
                    symbol = panelConfigs[panelId]?.symbol ?: "BTCUSDT",
                    providerRef = panelConfigs[panelId]?.providerRef ?: "main",
                    state = when (newType) {
                        PanelType.CHART -> PanelState.Chart()
                        PanelType.DOM -> PanelState.Dom()
                        PanelType.TRADES -> PanelState.Trades()
                        PanelType.TRADING -> PanelState.Trading()
                    }
                )
                panelConfigs = panelConfigs + (newPanelId to newConfig)
                persistConfig()
            },
            onMovePanel = { panelId, targetPanelId, zone ->
                history.push(snapshot())
                layoutState = LayoutEngine.movePanel(layoutState, panelId, targetPanelId, zone)
                persistConfig()
            },
            onMovePanelToRoot = { panelId, zone ->
                history.push(snapshot())
                layoutState = LayoutEngine.movePanelToRoot(layoutState, panelId, zone, ratio = 0.5f)
                persistConfig()
            },
            // Контролы в верхнем тайтле панели: у графика — символ/режим/
            // агрегация footprint/ТФ/Trading, у остальных — имя панели.
            panelTitleContent = { panelId ->
                panelConfigs[panelId]?.let { pc ->
                    when (pc.type) {
                        PanelType.CHART -> {
                            val vm = ws.liveViewModels.getOrPut(panelVmKey(pc)) {
                                koinInject<ChartViewModel>()
                            } as ChartViewModel
                            ChartTitleToolbar(
                                chartViewModel = vm,
                                onManageWatchlist = onManageWatchlist,
                                indicatorPresetRepository = koinInject(),
                            )
                        }

                        PanelType.DOM -> {
                            val vm = ws.liveViewModels.getOrPut("${pc.id}_dom") {
                                koinInject<DomViewModel>()
                            } as DomViewModel
                            val options by vm.domOptions.collectAsState()
                            val rows by vm.explorerRows.collectAsState()
                            SymbolExplorerDropdown(
                                currentSymbol = options.symbol.symbol,
                                currentExchangeId = options.provider,
                                rows = rows,
                                onSelect = vm::selectExplorerRow,
                                onManageWatchlist = onManageWatchlist,
                            )
                        }

                        PanelType.TRADES -> {
                            val vm = ws.liveViewModels.getOrPut("${pc.id}_trades") {
                                koinInject<TradesViewModel>()
                            } as TradesViewModel
                            val symbol by vm.currentSymbol.collectAsState()
                            val selectedProvider by vm.currentProviderId.collectAsState()
                            val rows by vm.explorerRows.collectAsState()
                            SymbolExplorerDropdown(
                                currentSymbol = symbol,
                                currentExchangeId = selectedProvider,
                                rows = rows,
                                onSelect = vm::selectExplorerRow,
                                onManageWatchlist = onManageWatchlist,
                            )
                        }

                        PanelType.TRADING -> {
                            val vm = ws.liveViewModels.getOrPut(panelVmKey(pc)) {
                                koinInject<TradingViewModel>()
                            } as TradingViewModel
                            TradingTitleDropdown(
                                vm = vm,
                                pc = pc,
                                onPanelConfigChange = ::updatePanelConfig,
                                onManageWatchlist = onManageWatchlist,
                            )
                        }
                    }
                }
            },
            // Меню шестерёнки тайтла: у графика — настройки графика,
            // у Trades — ширина панели; у остальных пока закрытие панели.
            panelSettingsContent = { panelId, dismiss ->
                panelConfigs[panelId]?.let { pc ->
                    when (pc.type) {
                        PanelType.CHART -> {
                            val vm = ws.liveViewModels.getOrPut(panelVmKey(pc)) {
                                koinInject<ChartViewModel>()
                            } as ChartViewModel
                            ChartSettingsMenu(chartViewModel = vm, dismiss = dismiss)
                        }

                        PanelType.TRADES -> {
                            val vm = ws.liveViewModels.getOrPut("${pc.id}_trades") {
                                koinInject<TradesViewModel>()
                            } as TradesViewModel
                            TradesWidthMenuContent(vm)
                        }

                        else -> PanelCloseMenuItem {
                            dismiss()
                            closePanel(panelId)
                        }
                    }
                }
            },
        ) { panelId ->
            panelConfigs[panelId]?.let { pc ->
                when (pc.type) {
                    PanelType.CHART -> ChartPanel(
                        ws, pc, providerIdForPanel(pc, providerRegistry),
                        onPanelConfigChange = ::updatePanelConfig,
                        onProviderChange = ::updatePanelProvider,
                    )
                    PanelType.DOM -> DomPanel(
                        ws, pc, providerIdForPanel(pc, providerRegistry),
                        providerDisplayName = providerRegistry::displayName,
                        onPanelConfigChange = ::updatePanelConfig,
                    )
                    PanelType.TRADES -> TradesPanel(
                        ws, pc, tradesWidths, providerIdForPanel(pc, providerRegistry),
                        onPanelConfigChange = ::updatePanelConfig,
                        onProviderChange = ::updatePanelProvider,
                    )
                    PanelType.TRADING -> TradingPanel(
                        ws, pc, providerIdForPanel(pc, providerRegistry),
                        onPanelConfigChange = ::updatePanelConfig,
                        onProviderChange = ::updatePanelProvider,
                    )
                }
            }
        }
    }
}

@Composable
private fun TradingTitleDropdown(
    vm: TradingViewModel,
    pc: PanelConfig,
    onPanelConfigChange: (PanelConfig) -> Unit,
    onManageWatchlist: (() -> Unit)?,
) {
    val rows by vm.explorerRows.collectAsState()
    val accountProviderId by vm.providerId.collectAsState()
    val state = pc.state as? PanelState.Trading ?: PanelState.Trading()
    val currentProviderId = pc.providerId ?: accountProviderId
    SymbolExplorerDropdown(
        currentSymbol = pc.symbol,
        currentExchangeId = currentProviderId,
        rows = rows,
        onSelect = { row ->
            onPanelConfigChange(
                pc.copy(
                    symbol = row.symbol,
                    providerId = row.exchangeId,
                    providerName = row.exchangeName,
                    state = state.copy(showAll = false),
                )
            )
        },
        onSelectAll = {
            onPanelConfigChange(pc.copy(state = state.copy(showAll = true)))
        },
        allSelected = state.showAll,
        onManageWatchlist = onManageWatchlist,
    )
}

@Composable
private fun PanelCloseMenuItem(onClick: () -> Unit) {
    TerminalMenuItem(text = "Close panel", onClick = onClick)
}

@Composable
private fun ChartPanel(
    ws: WorkspaceViewModel,
    pc: PanelConfig,
    providerId: String?,
    onPanelConfigChange: (PanelConfig) -> Unit,
    onProviderChange: (PanelConfig, String) -> Unit,
) {
    val vmKey = "${pc.id}_chart"
    val vm: ChartViewModel =
        ws.liveViewModels.getOrPut(vmKey) { koinInject<ChartViewModel>() } as ChartViewModel

    // panel-конфиг (персист): timeframe/chartMode/zoomLevel
    val state = pc.state as? PanelState.Chart
    val tf = state?.timeframe ?: "1m"

    LaunchedEffect(pc.id) {
        val savedMode = state?.chartMode ?: "CANDLESTICK"
        val target = try {
            ChartMode.valueOf(savedMode)
        } catch (e: Exception) {
            ChartMode.CANDLESTICK
        }
        if (vm.state.value.chartMode != target) vm.dispatch(ChartIntent.SelectChartMode(target))
    }

    LaunchedEffect(pc.symbol, tf) {
        val needReload =
            vm.state.value.currentSymbol != pc.symbol || vm.state.value.currentTimeframe != tf
        if (needReload) vm.dispatch(ChartIntent.LoadChart(pc.symbol, tf))
    }

    val currentPc by rememberUpdatedState(pc)
    LaunchedEffect(Unit) {
        var skipInitial = true
        vm.state.map { it.currentSymbol }.distinctUntilChanged().collect { s ->
            if (skipInitial) { skipInitial = false; return@collect }
            onPanelConfigChange(currentPc.copy(symbol = s))
        }
    }
    LaunchedEffect(Unit) {
        var skipInitial = true
        vm.state.map { it.currentTimeframe }.distinctUntilChanged().collect { tf2 ->
            if (skipInitial) { skipInitial = false; return@collect }
            val curS = currentPc.state as? PanelState.Chart ?: PanelState.Chart()
            onPanelConfigChange(currentPc.copy(state = curS.copy(timeframe = tf2)))
        }
    }
    LaunchedEffect(Unit) {
        var skipInitial = true
        vm.state.map { it.chartMode }.distinctUntilChanged().collect { mode ->
            if (skipInitial) { skipInitial = false; return@collect }
            val curS = currentPc.state as? PanelState.Chart ?: PanelState.Chart()
            onPanelConfigChange(currentPc.copy(state = curS.copy(chartMode = mode.name)))
        }
    }
    // Per-panel биржа: пишем в конфиг только после восстановления и только
    // на последующие (пользовательские) изменения — не затираем персист.
    LaunchedEffect(pc.id) {
        vm.providerRestored.first { it }
        var skipInitial = true
        vm.state.map { it.currentProviderId }.distinctUntilChanged().collect { id ->
            if (skipInitial) { skipInitial = false; return@collect }
            onProviderChange(currentPc, id)
        }
    }

    ChartWindow(
        vm,
        initialZoomLevel = state?.zoomLevel ?: 1f,
        onZoomChange = { zl ->
            val curS = (currentPc.state as? PanelState.Chart) ?: PanelState.Chart()
            onPanelConfigChange(currentPc.copy(state = curS.copy(zoomLevel = zl)))
        },
        workspaceId = ws.config.id,
        panelId = pc.id,
        initialProviderId = providerId,
    )
}

@Composable
private fun DomPanel(
    ws: WorkspaceViewModel,
    pc: PanelConfig,
    providerId: String?,
    providerDisplayName: (String) -> String,
    onPanelConfigChange: (PanelConfig) -> Unit,
) {
    val vmKey = "${pc.id}_dom"
    val vm: DomViewModel =
        ws.liveViewModels.getOrPut(vmKey) { koinInject<DomViewModel>() } as DomViewModel
    val domState = pc.state as? PanelState.Dom

    // Paper-режим и все panel-scoped настройки DOM восстанавливаются по
    // (workspace, panel). Провайдер применяет только attachPanel.
    LaunchedEffect(ws.config.id, pc.id, providerId) {
        vm.attachPanel(ws.config.id, pc.id, providerId)
    }

    // Символ/глубина/агрегация — только ПОСЛЕ восстановления провайдера,
    // иначе дефолтный Binance успевает перетереть сохранённую биржу.
    LaunchedEffect(pc.symbol, providerId) {
        vm.providerRestored.first { it }
        val current = vm.domOptions.value
        var opts = current.copy(
            symbol = TradingSymbol.fallback(pc.symbol, current.provider)
        )
        val savedAgg = domState?.aggregation
        if (savedAgg != null) {
            try {
                opts = opts.copy(aggregation = AggregationLevel.fromString(savedAgg))
            } catch (_: Exception) {
            }
        }
        val savedDepth = domState?.depth
        if (savedDepth != null && savedDepth > 0) {
            opts = opts.copy(
                depth = com.aandios.nous.feature.dom.domain.model.DepthLimit.create(savedDepth)
            )
        }
        vm.updateDomOptions(opts)
    }

    // Один атомарный писатель PanelConfig: символ/настройки/провайдер.
    // Два отдельных коллектора перетирали providerId устаревшим domPc.
    val domPc by rememberUpdatedState(pc)
    LaunchedEffect(Unit) {
        vm.providerRestored.first { it }
        vm.domOptions.collect { opts ->
            val curState = domPc.state as? PanelState.Dom ?: PanelState.Dom()
            val aggStr = when (opts.aggregation) {
                AggregationLevel.BaseTick -> "1x"
                AggregationLevel.TenTick -> "10x"
                AggregationLevel.HundredTick -> "100x"
            }
            onPanelConfigChange(
                domPc.copy(
                    symbol = opts.symbol.symbol.ifEmpty { domPc.symbol },
                    providerId = opts.provider,
                    providerName = providerDisplayName(opts.provider),
                    state = curState.copy(
                        depth = opts.depth.value,
                        aggregation = aggStr
                    )
                )
            )
        }
    }
    key(ws.activationCount) { DomWindow(vm) }
}

@Composable
private fun TradesPanel(
    ws: WorkspaceViewModel,
    pc: PanelConfig,
    tradesWidths: MutableMap<String, androidx.compose.ui.unit.Dp>,
    providerId: String?,
    onPanelConfigChange: (PanelConfig) -> Unit,
    onProviderChange: (PanelConfig, String) -> Unit,
) {
    val vmKey = "${pc.id}_trades"
    val vm: TradesViewModel =
        ws.liveViewModels.getOrPut(vmKey) { koinInject<TradesViewModel>() } as TradesViewModel

    // Своя биржа/контракты + персист ширины этой панели
    LaunchedEffect(pc.id, pc.symbol, providerId) {
        vm.attachPanel(ws.workspaceId, pc.id, providerId)
        if (vm.currentSymbol.value != pc.symbol) vm.subscribeToTrades(pc.symbol)
    }
    LaunchedEffect(Unit) {
        vm.panelWidthDp.collect { widthDp ->
            if (widthDp != null) tradesWidths[pc.id] = widthDp.dp
        }
    }

    val tradesPc by rememberUpdatedState(pc)
    LaunchedEffect(pc.id) {
        vm.providerRestored.first { it }
        var skipInitial = true
        vm.currentProviderId.collect { id ->
            if (skipInitial) { skipInitial = false; return@collect }
            onProviderChange(tradesPc, id)
        }
    }
    LaunchedEffect(Unit) {
        var skipInitial = true
        vm.currentSymbol.collect { s ->
            if (skipInitial) {
                skipInitial = false; return@collect
            }
            onPanelConfigChange(tradesPc.copy(symbol = s))
        }
    }
    LaunchedEffect(Unit) {
        var skipInitial = true
        vm.selectedSizeFilter.collect { filter ->
            if (skipInitial) {
                skipInitial = false; return@collect
            }
            val serialized = when (filter) {
                is SizeFilter.All -> "All"
                is SizeFilter.MinQty -> "MinQty"
                is SizeFilter.MinQtyx10 -> "MinQtyx10"
                is SizeFilter.MinQtyx100 -> "MinQtyx100"
                is SizeFilter.Custom -> "Custom:${filter.value}"
            }
            val curState = tradesPc.state as? PanelState.Trades ?: PanelState.Trades()
            onPanelConfigChange(tradesPc.copy(state = curState.copy(sizeFilter = serialized)))
        }
    }
    LaunchedEffect(Unit) {
        var skipInitial = true
        vm.customPresets.collect { presets ->
            if (skipInitial) {
                skipInitial = false; return@collect
            }
            val curState = tradesPc.state as? PanelState.Trades ?: PanelState.Trades()
            onPanelConfigChange(tradesPc.copy(state = curState.copy(customPresets = presets)))
        }
    }
    LaunchedEffect(pc.id) {
        val tradesState = pc.state as? PanelState.Trades
        if (tradesState != null) {
            if (tradesState.customPresets.isNotEmpty()) {
                vm.setPresets(tradesState.customPresets)
            }
            val saved = tradesState.sizeFilter
            if (saved != null) {
                val restored = when {
                    saved == "All" -> SizeFilter.All
                    saved == "MinQty" -> SizeFilter.MinQty
                    saved == "MinQtyx10" -> SizeFilter.MinQtyx10
                    saved == "MinQtyx100" -> SizeFilter.MinQtyx100
                    saved.startsWith("Custom:") -> saved.removePrefix("Custom:")
                        .toDoubleOrNull()
                        ?.let { SizeFilter.Custom(it) }
                        ?: SizeFilter.All
                    else -> SizeFilter.All
                }
                if (restored !is SizeFilter.All || vm.selectedSizeFilter.value !is SizeFilter.All) {
                    vm.updateSizeFilter(restored)
                }
            }
        }
    }
    TradesWindow(
        vm,
        currentSymbol = pc.symbol,
    )
}

@Composable
private fun TradingPanel(
    ws: WorkspaceViewModel,
    pc: PanelConfig,
    providerId: String?,
    onPanelConfigChange: (PanelConfig) -> Unit,
    onProviderChange: (PanelConfig, String) -> Unit,
) {
    val vmKey = "${pc.id}_trading"
    val vm: TradingViewModel =
        ws.liveViewModels.getOrPut(vmKey) { koinInject<TradingViewModel>() } as TradingViewModel

    // Биржа/контракты/инструмент — свои у этой панели; ALL отключает фильтр
    val showAll = (pc.state as? PanelState.Trading)?.showAll == true
    val filterSymbol = if (showAll) null else pc.symbol
    LaunchedEffect(pc.id, pc.symbol, providerId, showAll) {
        vm.attachPanel(ws.workspaceId, pc.id, providerId, filterSymbol)
    }

    // Активная вкладка сохраняется в PanelState
    val tradingPc by rememberUpdatedState(pc)
    LaunchedEffect(pc.id) {
        val state = pc.state as? PanelState.Trading
        if (state != null) {
            vm.restoreTab(state.activeTab)
        }
    }
    LaunchedEffect(pc.id) {
        vm.providerRestored.first { it }
        var skipInitial = true
        vm.providerId.collect { id ->
            if (skipInitial) { skipInitial = false; return@collect }
            onProviderChange(tradingPc, id)
        }
    }
    LaunchedEffect(Unit) {
        var skipInitial = true
        vm.activeTab.collect { tab ->
            if (skipInitial) {
                skipInitial = false; return@collect
            }
            val curState = tradingPc.state as? PanelState.Trading ?: PanelState.Trading()
            onPanelConfigChange(tradingPc.copy(state = curState.copy(activeTab = tab)))
        }
    }
    key(ws.activationCount) { TradingWindow(vm) }
}

private fun providerIdForPanel(
    panel: PanelConfig,
    registry: ProviderRegistry,
): String? = panel.providerId?.takeIf { registry.get(it) != null }
