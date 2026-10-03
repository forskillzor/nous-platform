/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.aandios.nous.core.ui.workspace.LayoutRenderer
import com.aandios.nous.core.workspace.*
import com.aandios.nous.core.workspace.viewmodel.WorkspaceViewModel
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
) {
    val scope = rememberCoroutineScope()

    var panelConfigs by remember(ws.config.id) { mutableStateOf(ws.config.panels.associateBy { it.id }) }
    var layoutState by remember(ws.config.id) { mutableStateOf(ws.config.layout) }

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

    fun disposePanelVm(pc: PanelConfig) {
        val suffix = when (pc.type) {
            PanelType.CHART -> "_chart"
            PanelType.DOM -> "_dom"
            PanelType.TRADES -> "_trades"
        }
        val vmKey = "${pc.id}$suffix"
        (ws.liveViewModels.remove(vmKey) as? com.aandios.nous.core.Disposable)?.dispose()
    }

    // Корень — фокусируемый, чтобы ловить Ctrl+Z / Ctrl+Shift+Z (undo/redo сплитов).
    // Если истории нет — не потребляем событие: оно уйдёт к undo рисунков графика.
    val focusInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
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
            onRatioChange = { persistConfig() },
            onRatioChangeStart = { history.push(snapshot()) },
            onClosePanel = { panelId ->
                val newLayout = LayoutEngine.removePanel(layoutState, panelId)
                if (newLayout != null) {
                    history.push(snapshot())
                    layoutState = newLayout
                    val removedPc = panelConfigs[panelId]
                    panelConfigs = panelConfigs - panelId
                    removedPc?.let { disposePanelVm(it) }
                    persistConfig()
                }
            },
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
                    }
                )
                panelConfigs = panelConfigs + (newPanelId to newConfig)
                persistConfig()
            },
            onMovePanel = { panelId, targetPanelId, zone ->
                history.push(snapshot())
                layoutState = LayoutEngine.movePanel(layoutState, panelId, targetPanelId, zone)
                persistConfig()
            }
        ) { panelId ->
            panelConfigs[panelId]?.let { pc ->
                when (pc.type) {
                    PanelType.CHART -> ChartPanel(ws, pc, onPanelConfigChange = ::updatePanelConfig)
                    PanelType.DOM -> DomPanel(ws, pc, onPanelConfigChange = ::updatePanelConfig)
                    PanelType.TRADES -> TradesPanel(ws, pc, onPanelConfigChange = ::updatePanelConfig)
                }
            }
        }
    }
}

@Composable
private fun ChartPanel(
    ws: WorkspaceViewModel,
    pc: PanelConfig,
    onPanelConfigChange: (PanelConfig) -> Unit,
) {
    val vmKey = "${pc.id}_chart"
    val vm: ChartViewModel =
        ws.liveViewModels.getOrPut(vmKey) { koinInject<ChartViewModel>() } as ChartViewModel
    val state = pc.state as? PanelState.Chart
    val tf = state?.timeframe ?: "1m"

    LaunchedEffect(pc.id) {
        val savedMode = state?.chartMode ?: "CANDLESTICK"
        val target = try {
            ChartMode.valueOf(savedMode)
        } catch (e: Exception) {
            ChartMode.CANDLESTICK
        }
        if (vm.state.value.chartMode != target) vm.dispatch(ChartIntent.ToggleChartMode)
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
            if (skipInitial) {
                skipInitial = false; return@collect
            }
            onPanelConfigChange(currentPc.copy(symbol = s))
        }
    }
    LaunchedEffect(Unit) {
        var skipInitial = true
        vm.state.map { it.currentTimeframe }.distinctUntilChanged().collect { tf2 ->
            if (skipInitial) {
                skipInitial = false; return@collect
            }
            val curS = currentPc.state as? PanelState.Chart ?: PanelState.Chart()
            onPanelConfigChange(currentPc.copy(state = curS.copy(timeframe = tf2)))
        }
    }
    LaunchedEffect(Unit) {
        var skipInitial = true
        vm.state.map { it.chartMode }.distinctUntilChanged().collect { mode ->
            if (skipInitial) {
                skipInitial = false; return@collect
            }
            val curS = currentPc.state as? PanelState.Chart ?: PanelState.Chart()
            onPanelConfigChange(currentPc.copy(state = curS.copy(chartMode = mode.name)))
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
        panelId = pc.id
    )
}

@Composable
private fun DomPanel(
    ws: WorkspaceViewModel,
    pc: PanelConfig,
    onPanelConfigChange: (PanelConfig) -> Unit,
) {
    val vmKey = "${pc.id}_dom"
    val vm: DomViewModel =
        ws.liveViewModels.getOrPut(vmKey) { koinInject<DomViewModel>() } as DomViewModel
    val domState = pc.state as? PanelState.Dom

    LaunchedEffect(pc.symbol) {
        val ts = TradingSymbol.findSymbol(
            pc.symbol,
            com.aandios.nous.feature.dom.domain.TradingProvider.BINANCE
        ) ?: TradingSymbol(
            pc.symbol,
            pc.symbol,
            com.aandios.nous.feature.dom.domain.TradingProvider.BINANCE
        )
        var opts = vm.domOptions.value.copy(symbol = ts)
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

    val domPc by rememberUpdatedState(pc)
    LaunchedEffect(Unit) {
        var skipInitial = true
        vm.domOptions.collect { opts ->
            if (skipInitial) {
                skipInitial = false; return@collect
            }
            val curState = domPc.state as? PanelState.Dom ?: PanelState.Dom()
            val aggStr = when (opts.aggregation) {
                AggregationLevel.BaseTick -> "1x"
                AggregationLevel.TenTick -> "10x"
                AggregationLevel.HundredTick -> "100x"
            }
            onPanelConfigChange(
                domPc.copy(
                    symbol = opts.symbol.symbol.ifEmpty { domPc.symbol },
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
    onPanelConfigChange: (PanelConfig) -> Unit,
) {
    val vmKey = "${pc.id}_trades"
    val vm: TradesViewModel =
        ws.liveViewModels.getOrPut(vmKey) { koinInject<TradesViewModel>() } as TradesViewModel

    val needReload = vm.currentSymbol.value != pc.symbol
    LaunchedEffect(pc.symbol) { if (needReload) vm.subscribeToTrades(pc.symbol) }

    val tradesPc by rememberUpdatedState(pc)
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
        onSymbolChanged = { s -> vm.subscribeToTrades(s) }
    )
}
