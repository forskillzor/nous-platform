/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.ui.component.TerminalMenuItem
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
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

    // Внешние обновления конфига («Apply symbol to current workspace»): символы и
    // провайдеры панелей меняются вне WorkspaceView — подтягиваем их в локальный
    // state; layout и panel-настройки остаются локальными.
    LaunchedEffect(ws.config.id) {
        snapshotFlow { ws.config }.collect { cfg ->
            val incoming = cfg.panels.associateBy { it.id }
            var next = panelConfigs
            incoming.forEach { (id, remote) ->
                val local = next[id] ?: return@forEach
                if (local.symbol != remote.symbol || local.providerRef != remote.providerRef) {
                    next = next + (id to local.copy(symbol = remote.symbol, providerRef = remote.providerRef))
                }
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
            // Контролы в верхнем тайтле панели: у графика — символ/режим/ТФ/
            // Trading/AI, у остальных — имя панели.
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
                            )
                        }

                        PanelType.DOM -> PanelTitleLabel(text = "DOM", stream = true)
                        PanelType.TRADES -> PanelTitleLabel(text = "Trades", glyph = "⇥")
                        PanelType.TRADING -> PanelTitleLabel(text = "Trading")
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
                        ws, pc, providerIdForPanel(ws.config, pc, providerRegistry),
                        onPanelConfigChange = ::updatePanelConfig,
                    )
                    PanelType.DOM -> DomPanel(
                        ws, pc, providerIdForPanel(ws.config, pc, providerRegistry),
                        onPanelConfigChange = ::updatePanelConfig,
                    )
                    PanelType.TRADES -> TradesPanel(
                        ws, pc, tradesWidths, providerIdForPanel(ws.config, pc, providerRegistry),
                        onPanelConfigChange = ::updatePanelConfig,
                    )
                    PanelType.TRADING -> TradingPanel(ws, pc, onPanelConfigChange = ::updatePanelConfig)
                }
            }
        }
    }
}

/** Имя панели в тайтле (DOM/Trades/Trading): глиф + текст + live-индикатор. */
@Composable
private fun PanelTitleLabel(text: String, glyph: String? = null, stream: Boolean = false) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        glyph?.let {
            Text(
                text = it,
                color = tokens.colors.accent,
                fontSize = tokens.typography.controlSize,
                fontFamily = TerminalFontFamily,
            )
        }
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = tokens.typography.controlSize,
            fontWeight = FontWeight.Medium,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
        )
        if (stream) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tokens.colors.bullish),
            )
            Text(
                text = "STREAM",
                color = tokens.colors.bullish,
                fontSize = tokens.typography.smallSize,
                fontFamily = TerminalFontFamily,
                letterSpacing = tokens.typography.compactLetterSpacing,
            )
        }
    }
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
        panelId = pc.id,
        initialProviderId = providerId,
    )
}

@Composable
private fun DomPanel(
    ws: WorkspaceViewModel,
    pc: PanelConfig,
    providerId: String?,
    onPanelConfigChange: (PanelConfig) -> Unit,
) {
    val vmKey = "${pc.id}_dom"
    val vm: DomViewModel =
        ws.liveViewModels.getOrPut(vmKey) { koinInject<DomViewModel>() } as DomViewModel
    val domState = pc.state as? PanelState.Dom

    // Paper-режим DOM-панели персистится и восстанавливается по panelId
    LaunchedEffect(pc.id, providerId) {
        vm.attachPanel(pc.id, providerId)
    }

    LaunchedEffect(pc.symbol, providerId) {
        val targetProvider = providerId ?: vm.domOptions.value.provider
        val ts = TradingSymbol.fallback(pc.symbol, targetProvider)
        var opts = vm.domOptions.value.copy(symbol = ts, provider = targetProvider)
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
    tradesWidths: MutableMap<String, androidx.compose.ui.unit.Dp>,
    providerId: String?,
    onPanelConfigChange: (PanelConfig) -> Unit,
) {
    val vmKey = "${pc.id}_trades"
    val vm: TradesViewModel =
        ws.liveViewModels.getOrPut(vmKey) { koinInject<TradesViewModel>() } as TradesViewModel

    LaunchedEffect(pc.id, pc.symbol, providerId) {
        providerId?.let(vm::selectProvider)
        if (vm.currentSymbol.value != pc.symbol) vm.subscribeToTrades(pc.symbol)
    }

    // Персист ширины панели (шестерёнка): ширина для LayoutRenderer
    LaunchedEffect(pc.id) { vm.attachPanel(pc.id) }
    LaunchedEffect(Unit) {
        vm.panelWidthDp.collect { widthDp ->
            if (widthDp != null) tradesWidths[pc.id] = widthDp.dp
        }
    }

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
    )
}

@Composable
private fun TradingPanel(
    ws: WorkspaceViewModel,
    pc: PanelConfig,
    onPanelConfigChange: (PanelConfig) -> Unit,
) {
    val vmKey = "${pc.id}_trading"
    val vm: TradingViewModel =
        ws.liveViewModels.getOrPut(vmKey) { koinInject<TradingViewModel>() } as TradingViewModel

    // Активная вкладка сохраняется в PanelState
    val tradingPc by rememberUpdatedState(pc)
    LaunchedEffect(pc.id) {
        val state = pc.state as? PanelState.Trading
        if (state != null) {
            vm.restoreTab(state.activeTab)
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
    workspace: WorkspaceConfig,
    panel: PanelConfig,
    registry: ProviderRegistry,
): String? {
    val configuredProviderId = workspace.providers
        .firstOrNull { it.id == panel.providerRef }
        ?.providerId
        ?: return null
    return configuredProviderId.takeIf { registry.get(it) != null }
}
