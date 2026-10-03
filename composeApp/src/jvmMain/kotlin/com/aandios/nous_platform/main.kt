/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import com.aandios.nous.core.ui.workspace.LayoutRenderer
import com.aandios.nous.core.ui.workspace.TabBar
import com.aandios.nous.core.ui.workspace.WelcomeScreen
import com.aandios.nous.core.workspace.*
import com.aandios.nous.core.workspace.viewmodel.TabManager
import com.aandios.nous.feature.chart.ui.ChartIntent
import com.aandios.nous.feature.chart.ui.ChartMode
import com.aandios.nous.feature.chart.ui.ChartViewModel
import com.aandios.nous.feature.chart.ui.ChartWindow
import com.aandios.nous.feature.dom.domain.TradingSymbol
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import com.aandios.nous.feature.dom.ui.DomViewModel
import com.aandios.nous.feature.dom.ui.DomWindow
import com.aandios.nous.feature.trades.ui.SizeFilter
import com.aandios.nous.feature.trades.ui.TradesViewModel
import com.aandios.nous.feature.trades.ui.TradesWindow
import com.aandios.nous_platform.di.initKoin
import com.aandios.nous_platform.ui.terminalLayout.TerminalLayout
import com.aandios.nous_platform.ui.terminalLayout.TerminalStateViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

fun main() = application {
    initKoin()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Nous Platform • v 0.1",
        state = rememberWindowState(width = 1900.dp, height = 1080.dp)
    ) {
        TradingTerminalTheme(
            darkTheme = true, // Всегда темная тема
            nightMode = false // Можно добавить переключатель
        ) {
            val chartViewModel: ChartViewModel = koinInject()
            val domViewModel: DomViewModel = koinInject()
            val tradesViewModel: TradesViewModel = koinInject()
            val terminalStateViewModel: TerminalStateViewModel = koinInject()

            // Workspace system
            val tabManager: TabManager = koinInject()
            val workspaceRepo: WorkspaceRepository = koinInject()
            val templateRepo: TemplateRepository = koinInject()
            val scope = rememberCoroutineScope()

            // Restore workspace session on startup
            LaunchedEffect(Unit) { tabManager.restoreSession() }

            TerminalLayout(
                modifier = Modifier.fillMaxHeight(),
                terminalState = terminalStateViewModel,
                tabManager = tabManager,
                workspaceRepo = workspaceRepo,
                templateRepo = templateRepo,
                onOpenWorkspace = { config ->
                    scope.launch { tabManager.openWorkspace(config) }
                },
                onSymbolSelected = { symbol ->
                    terminalStateViewModel.changeSymbol(symbol)
                    val timeframe = terminalStateViewModel.selectedTimeFrame.value
                    chartViewModel.dispatch(ChartIntent.LoadChart(symbol, timeframe))
                    val currentOptions = domViewModel.domOptions.value
                    domViewModel.updateDomOptions(
                        currentOptions.copy(
                            symbol = TradingSymbol.findSymbol(symbol, currentOptions.provider)
                                ?: TradingSymbol(symbol, symbol, currentOptions.provider)
                        )
                    )
                    tradesViewModel.subscribeToTrades(symbol)
                },
                onTimeframeSelected = { timeframe ->
                    terminalStateViewModel.changeTimeFrame(timeframe)
                    val symbol = terminalStateViewModel.selectedSymbol.value
                    chartViewModel.dispatch(ChartIntent.LoadChart(symbol, timeframe))
                },
            ) {
                // Main content — workspace tabs or welcome screen
                val workspaces by tabManager.workspaces.collectAsState()
                val activeIdx by tabManager.activeIndex.collectAsState()
                var recentConfigs by remember { mutableStateOf<List<WorkspaceConfig>>(emptyList()) }
                var userTemplates by remember { mutableStateOf<List<WorkspaceConfig>>(emptyList()) }
                var hiddenBuiltins by remember { mutableStateOf<Set<String>>(emptySet()) }
                LaunchedEffect(workspaces.size) {
                    recentConfigs = workspaceRepo.getAll()
                        .sortedByDescending { it.updatedAt }
                        .take(8)
                    userTemplates = templateRepo.getAll()
                    hiddenBuiltins = templateRepo.hiddenBuiltins()
                }
                if (workspaces.isEmpty()) {
                    WelcomeScreen(
                        recentWorkspaces = recentConfigs,
                        userTemplates = userTemplates,
                        hiddenBuiltinTemplates = hiddenBuiltins,
                        onSelectTemplate = { config ->
                            scope.launch {
                                val fresh = config.copy(
                                    id = generateId(),
                                    createdAt = currentTime(),
                                    updatedAt = currentTime()
                                )
                                workspaceRepo.create(fresh)
                                tabManager.openWorkspace(fresh)
                            }
                        },
                        onOpenRecent = { config ->
                            scope.launch { tabManager.openWorkspace(config) }
                        },
                        onDeleteTemplate = { template ->
                            scope.launch {
                                templateRepo.delete(template.id)
                                userTemplates = templateRepo.getAll()
                            }
                        },
                        onHideBuiltinTemplate = { builtinId ->
                            scope.launch {
                                templateRepo.hideBuiltin(builtinId)
                                hiddenBuiltins = templateRepo.hiddenBuiltins()
                            }
                        },
                        onDeleteRecent = { config ->
                            scope.launch {
                                tabManager.closeWorkspace(config.id)
                                workspaceRepo.delete(config.id)
                                recentConfigs = workspaceRepo.getAll()
                                    .sortedByDescending { it.updatedAt }
                                    .take(8)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    Column(Modifier.fillMaxWidth().weight(1f)) {
                        TabBar(
                            workspaces = workspaces,
                            activeIndex = activeIdx,
                            onTabClick = { tabManager.setActive(it) },
                            onTabClose = { scope.launch { tabManager.closeWorkspace(it) } },
                            onTabReorder = { from, to -> tabManager.reorderWorkspace(from, to) }
                        )
                        Box(Modifier.fillMaxSize()) {
                            tabManager.activeWorkspace?.let { ws ->
                                    var panelConfigs by remember(ws.config.id) { mutableStateOf(ws.config.panels.associateBy { it.id }) }
                                    var layoutState by remember(ws.config.id) { mutableStateOf(ws.config.layout) }

                                    fun persistConfig() {
                                        val config = ws.config.copy(
                                            layout = layoutState,
                                            panels = panelConfigs.values.toList()
                                        )
                                        ws.updateConfig(config)
                                        scope.launch { workspaceRepo.update(config) }
                                    }

                                    LayoutRenderer(
                                        node = layoutState,
                                        panels = panelConfigs,
                                        onRatioChange = { persistConfig() },
                                        onClosePanel = { panelId ->
                                            val newLayout = LayoutEngine.removePanel(layoutState, panelId)
                                            if (newLayout != null) {
                                                layoutState = newLayout
                                                val removedPc = panelConfigs[panelId]
                                                panelConfigs = panelConfigs - panelId
                                                // Dispose the removed panel's ViewModel
                                                removedPc?.let { pc ->
                                                    val suffix = when (pc.type) {
                                                        PanelType.CHART -> "_chart"
                                                        PanelType.DOM -> "_dom"
                                                        PanelType.TRADES -> "_trades"
                                                    }
                                                    val vmKey = "${pc.id}$suffix"
                                                    (ws.liveViewModels.remove(vmKey) as? com.aandios.nous.core.Disposable)?.dispose()
                                                }
                                                persistConfig()
                                            }
                                        },
                                        onSplitPanel = { panelId, direction, newType ->
                                            val newPanelId = "panel-${generateId()}"
                                            val newLayout =
                                                LayoutEngine.split(layoutState, panelId, direction, newPanelId)
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
                                                }
                                            )
                                            panelConfigs = panelConfigs + (newPanelId to newConfig)
                                            persistConfig()
                                        }
                                    ) { panelId ->
                                        panelConfigs[panelId]?.let { pc ->
                                            when (pc.type) {
                                                PanelType.CHART -> {
                                                    val vmKey = "${pc.id}_chart"
                                                    val vm: ChartViewModel =
                                                        ws.liveViewModels.getOrPut(vmKey) { koinInject<ChartViewModel>() } as ChartViewModel
                                                    val state =
                                                        pc.state as? PanelState.Chart
                                                    val tf = state?.timeframe ?: "1m"
                                                    // Restore chartMode from config
                                                    LaunchedEffect(pc.id) {
                                                        val savedMode = state?.chartMode ?: "CANDLESTICK"
                                                        val target = try {
                                                            ChartMode.valueOf(savedMode)
                                                        } catch (e: Exception) {
                                                            ChartMode.CANDLESTICK
                                                        }
                                                        if (vm.state.value.chartMode != target) vm.dispatch(ChartIntent.ToggleChartMode)
                                                    }
                                                    // Only reload if symbol or timeframe changed since last load
                                                    LaunchedEffect(pc.symbol, tf) {
                                                        val needReload =
                                                            vm.state.value.currentSymbol != pc.symbol || vm.state.value.currentTimeframe != tf
                                                        if (needReload) vm.dispatch(
                                                            ChartIntent.LoadChart(
                                                                pc.symbol,
                                                                tf
                                                            )
                                                        )
                                                    }
                                                    // Sync back: when user changes symbol/timeframe/zoom/chartMode → update PanelConfig
                                                    val currentPc by rememberUpdatedState(pc)
                                                    LaunchedEffect(Unit) {
                                                        var skipInitial = true
                                                        vm.state.map { it.currentSymbol }.distinctUntilChanged()
                                                            .collect { s ->
                                                                if (skipInitial) {
                                                                    skipInitial = false; return@collect
                                                                }
                                                                panelConfigs =
                                                                    panelConfigs + (currentPc.id to currentPc.copy(
                                                                        symbol = s
                                                                    )); persistConfig()
                                                            }
                                                    }
                                                    LaunchedEffect(Unit) {
                                                        var skipInitial = true
                                                        vm.state.map { it.currentTimeframe }.distinctUntilChanged()
                                                            .collect { tf2 ->
                                                                if (skipInitial) {
                                                                    skipInitial = false; return@collect
                                                                }
                                                                val curS = currentPc.state as? PanelState.Chart
                                                                    ?: PanelState.Chart()
                                                                panelConfigs =
                                                                    panelConfigs + (currentPc.id to currentPc.copy(
                                                                        state = curS.copy(timeframe = tf2)
                                                                    )); persistConfig()
                                                            }
                                                    }
                                                    LaunchedEffect(Unit) {
                                                        var skipInitial = true
                                                        vm.state.map { it.chartMode }.distinctUntilChanged()
                                                            .collect { mode ->
                                                                if (skipInitial) {
                                                                    skipInitial = false; return@collect
                                                                }
                                                                val curS = currentPc.state as? PanelState.Chart
                                                                    ?: PanelState.Chart()
                                                                panelConfigs =
                                                                    panelConfigs + (currentPc.id to currentPc.copy(
                                                                        state = curS.copy(chartMode = mode.name)
                                                                    )); persistConfig()
                                                            }
                                                    }
                                                    ChartWindow(
                                                        vm,
                                                        initialZoomLevel = state?.zoomLevel ?: 1f,
                                                        onZoomChange = { zl ->
                                                            val curS = (currentPc.state as? PanelState.Chart)
                                                                ?: PanelState.Chart()
                                                            panelConfigs =
                                                                panelConfigs + (currentPc.id to currentPc.copy(
                                                                    state = curS.copy(zoomLevel = zl)
                                                                )); persistConfig()
                                                        },
                                                        workspaceId = ws.config.id,
                                                        panelId = pc.id
                                                    )
                                                }

                                                PanelType.DOM -> {
                                                    val vmKey = "${pc.id}_dom"
                                                    val vm: DomViewModel =
                                                        ws.liveViewModels.getOrPut(vmKey) { koinInject<DomViewModel>() } as DomViewModel
                                                    val domState = pc.state as? PanelState.Dom
                                                    LaunchedEffect(pc.symbol) {
                                                        val ts = TradingSymbol.findSymbol(
                                                            pc.symbol,
                                                            com.aandios.nous.feature.dom.domain.TradingProvider.BINANCE
                                                        )
                                                            ?: TradingSymbol(
                                                                pc.symbol,
                                                                pc.symbol,
                                                                com.aandios.nous.feature.dom.domain.TradingProvider.BINANCE
                                                            )
                                                        var opts = vm.domOptions.value.copy(symbol = ts)
                                                        // Restore aggregation from saved state
                                                        val savedAgg = domState?.aggregation
                                                        if (savedAgg != null) {
                                                            try {
                                                                opts = opts.copy(
                                                                    aggregation = AggregationLevel.fromString(savedAgg)
                                                                )
                                                            } catch (_: Exception) {
                                                            }
                                                        }
                                                        // Restore depth from saved state
                                                        val savedDepth = domState?.depth
                                                        if (savedDepth != null && savedDepth > 0) {
                                                            opts = opts.copy(
                                                                depth = com.aandios.nous.feature.dom.domain.model.DepthLimit.create(
                                                                    savedDepth
                                                                )
                                                            )
                                                        }
                                                        vm.updateDomOptions(opts)
                                                    }
                                                    // Sync back DOM options
                                                    val domPc by rememberUpdatedState(pc)
                                                    LaunchedEffect(Unit) {
                                                        var skipInitial = true
                                                        vm.domOptions.collect { opts ->
                                                            if (skipInitial) {
                                                                skipInitial = false; return@collect
                                                            }
                                                            val curState =
                                                                domPc.state as? PanelState.Dom ?: PanelState.Dom()
                                                            val aggStr = when (opts.aggregation) {
                                                                AggregationLevel.BaseTick -> "1x"
                                                                AggregationLevel.TenTick -> "10x"
                                                                AggregationLevel.HundredTick -> "100x"
                                                            }
                                                            panelConfigs = panelConfigs + (domPc.id to domPc.copy(
                                                                symbol = opts.symbol.symbol.ifEmpty { domPc.symbol },
                                                                state = curState.copy(
                                                                    depth = opts.depth.value,
                                                                    aggregation = aggStr
                                                                )
                                                            )); persistConfig()
                                                        }
                                                    }
                                                    key(ws.activationCount) { DomWindow(vm) }
                                                }

                                                PanelType.TRADES -> {
                                                    val vmKey = "${pc.id}_trades"
                                                    val vm: TradesViewModel =
                                                        ws.liveViewModels.getOrPut(vmKey) { koinInject<TradesViewModel>() } as TradesViewModel
                                                    val needReload = vm.currentSymbol.value != pc.symbol
                                                    LaunchedEffect(pc.symbol) { if (needReload) vm.subscribeToTrades(pc.symbol) }
                                                    // Sync symbol back
                                                    val tradesPc by rememberUpdatedState(pc)
                                                    LaunchedEffect(Unit) {
                                                        var skipInitial = true
                                                        vm.currentSymbol.collect { s ->
                                                            if (skipInitial) {
                                                                skipInitial = false; return@collect
                                                            }
                                                            panelConfigs =
                                                                panelConfigs + (tradesPc.id to tradesPc.copy(symbol = s)); persistConfig()
                                                        }
                                                    }
                                                    // Sync size filter back
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
                                                            val curState = tradesPc.state as? PanelState.Trades
                                                                ?: PanelState.Trades()
                                                            panelConfigs = panelConfigs + (tradesPc.id to tradesPc.copy(
                                                                state = curState.copy(sizeFilter = serialized)
                                                            )); persistConfig()
                                                        }
                                                    }
                                                    // Sync custom presets back
                                                    LaunchedEffect(Unit) {
                                                        var skipInitial = true
                                                        vm.customPresets.collect { presets ->
                                                            if (skipInitial) {
                                                                skipInitial = false; return@collect
                                                            }
                                                            val curState = tradesPc.state as? PanelState.Trades
                                                                ?: PanelState.Trades()
                                                            panelConfigs = panelConfigs + (tradesPc.id to tradesPc.copy(
                                                                state = curState.copy(customPresets = presets)
                                                            )); persistConfig()
                                                        }
                                                    }
                                                    // Restore filter + presets
                                                    LaunchedEffect(pc.id) {
                                                        val tradesState = pc.state as? PanelState.Trades
                                                        if (tradesState != null) {
                                                            // Restore presets
                                                            if (tradesState.customPresets.isNotEmpty()) {
                                                                vm.setPresets(tradesState.customPresets)
                                                            }
                                                            // Restore filter
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
                                                        onSymbolChanged = { s -> vm.subscribeToTrades(s) })
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }