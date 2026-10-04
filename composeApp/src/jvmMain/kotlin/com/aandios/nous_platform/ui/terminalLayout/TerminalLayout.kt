/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.core.ui.workspace.ProjectTree
import com.aandios.nous.core.workspace.TemplateRepository
import com.aandios.nous.core.workspace.WorkspaceBus
import com.aandios.nous.core.workspace.WorkspaceConfig
import com.aandios.nous.core.workspace.WorkspaceRepository
import com.aandios.nous.core.workspace.viewmodel.TabManager
import kotlinx.coroutines.launch
import nous_platform.composeapp.generated.resources.*

// Сначала создадим enum для типов инструментов
enum class ToolPanelType {
    WORKSPACES,
    SYMBOLS,
    INDICATORS,
    TIMEFRAMES,
    DRAWINGS,
    STRATEGIES
}

data class ToolPanelState(
    val isExpanded: Boolean = false,
    val type: ToolPanelType? = null,
    val width: Dp = 200.dp
)

enum class BottomToolType {
    PORTFOLIO,
    CONSOLE,
    EDITOR
}

enum class PortfolioTab {
    POSITIONS,
    ORDERS,
    BALANCE,
    STATS
}

// Мок данные для портфеля
data class MockPosition(
    val symbol: String,
    val side: TradeSide,
    val quantity: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val pnl: Double,
    val pnlPercent: Double
)

data class MockOrder(
    val id: String,
    val symbol: String,
    val side: TradeSide,
    val type: String, // "LIMIT" or "MARKET"
    val price: Double,
    val quantity: Double,
    val filled: Double,
    val timestamp: Long,
    val status: String // "OPEN", "FILLED", "CANCELLED"
)

data class MockBalance(
    val asset: String,
    val free: Double,
    val locked: Double,
    val total: Double,
    val usdValue: Double
)

/** Переключение выдвижной панели: тот же тип — свернуть/развернуть, другой — открыть. */
private fun ToolPanelState.toggle(type: ToolPanelType): ToolPanelState =
    if (this.type == type) copy(isExpanded = !isExpanded)
    else copy(isExpanded = true, type = type)

@Composable
fun TerminalLayout(
    modifier: Modifier = Modifier,
    terminalState: TerminalStateViewModel,
    onSymbolSelected: (String) -> Unit,
    onTimeframeSelected: (String) -> Unit,
    tabManager: TabManager? = null,
    workspaceRepo: WorkspaceRepository? = null,
    templateRepo: TemplateRepository? = null,
    workspaceBus: WorkspaceBus? = null,
    activeWorkspaceId: String? = null,
    onOpenWorkspace: ((WorkspaceConfig) -> Unit)? = null,
    mainContent: @Composable ColumnScope.() -> Unit,
) {
    var topPanelState by remember { mutableStateOf(ToolPanelState()) }
    var bottomPanelState by remember { mutableStateOf<BottomToolType?>(null) }
    var portfolioTab by remember { mutableStateOf(PortfolioTab.POSITIONS) }
    // Высота нижней панели запоминается на время сессии
    var bottomPanelHeight by remember { mutableStateOf(300.dp) }
    var contentAreaHeightPx by remember { mutableFloatStateOf(0f) }

    // Реактивные значения из VM (раньше читались один раз через remember)
    val selectedSymbol by terminalState.selectedSymbol.collectAsState()
    val selectedTimeframe by terminalState.selectedTimeFrame.collectAsState()

    // Workspace state
    val scope = rememberCoroutineScope()
    val useWorkspaces = tabManager != null && workspaceRepo != null
    var allWorkspaceConfigs by remember { mutableStateOf<List<WorkspaceConfig>>(emptyList()) }
    if (useWorkspaces) {
        // Реактивная загрузка: начальная + при каждом изменении через WorkspaceBus
        // (в т.ч. удаления из WelcomeScreen).
        LaunchedEffect(Unit) {
            allWorkspaceConfigs = workspaceRepo!!.getAll()
            workspaceBus?.version?.collect { allWorkspaceConfigs = workspaceRepo!!.getAll() }
        }
    }

    // Recollect after changes + уведомить подписчиков (WelcomeScreen)
    fun refreshWorkspaces() {
        scope.launch {
            allWorkspaceConfigs = workspaceRepo?.getAll() ?: emptyList()
        }
        workspaceBus?.workspaceChanged()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Тёмный фон под всем терминалом: иначе при анимациях панелей
            // в щелях проблёскивает фон AWT-окна.
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Основная панель с иконками (всегда видима)
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                // Верхняя группа иконок
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val topIcons = buildList {
                        if (useWorkspaces) {
                            add(Triple(ToolPanelType.WORKSPACES, Res.drawable.workspaces, "Workspaces"))
                        }
                        add(Triple(ToolPanelType.SYMBOLS, Res.drawable.candlestick, "Symbols"))
                        add(Triple(ToolPanelType.INDICATORS, Res.drawable.indicators, "Indicators"))
                        add(Triple(ToolPanelType.TIMEFRAMES, Res.drawable.clock, "Timeframes"))
                        add(Triple(ToolPanelType.DRAWINGS, Res.drawable.pencil, "Drawings"))
                        add(Triple(ToolPanelType.STRATEGIES, Res.drawable.robot, "Strategies"))
                    }
                    topIcons.forEach { (type, icon, description) ->
                        ToolBarIcon(
                            icon = icon,
                            description = description,
                            isSelected = topPanelState.type == type && topPanelState.isExpanded,
                            onClick = { topPanelState = topPanelState.toggle(type) }
                        )
                    }
                }

                // Разделитель
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )

                // Нижняя группа иконок
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val bottomIcons = listOf(
                        Triple(BottomToolType.PORTFOLIO, Res.drawable.wallet, "Portfolio"),
                        Triple(BottomToolType.CONSOLE, Res.drawable.terminal, "Console"),
                        Triple(BottomToolType.EDITOR, Res.drawable.code, "Editor"),
                    )
                    bottomIcons.forEach { (type, icon, description) ->
                        ToolBarIcon(
                            icon = icon,
                            description = description,
                            isSelected = bottomPanelState == type,
                            onClick = {
                                bottomPanelState = if (bottomPanelState == type) null else type
                            }
                        )
                    }
                }
            }

            // Верхняя выдвижная панель (слева, под иконками)
            if (topPanelState.isExpanded) {
                // Draggable handle state
                val sidebarInteractionSource = remember { MutableInteractionSource() }
                val sidebarHovered by sidebarInteractionSource.collectIsHoveredAsState()
                Row {
                    if (topPanelState.type == ToolPanelType.WORKSPACES && useWorkspaces) {
                        ProjectTree(
                            workspaces = allWorkspaceConfigs,
                            activeId = activeWorkspaceId,
                            onWorkspaceClick = { config -> onOpenWorkspace?.invoke(config) },
                            onNewWorkspace = {
                                scope.launch {
                                    val config = com.aandios.nous.core.workspace.Templates.scalping()
                                    workspaceRepo!!.create(config)
                                    onOpenWorkspace?.invoke(config)
                                    refreshWorkspaces()
                                }
                            },
                            onRename = { ws, newName ->
                                scope.launch { workspaceRepo?.update(ws.copy(name = newName)); refreshWorkspaces() }
                            },
                            onDelete = { ws ->
                                scope.launch { tabManager?.closeWorkspace(ws.id); workspaceRepo?.delete(ws.id); refreshWorkspaces() }
                            },
                            onExport = { ws ->
                                scope.launch {
                                    val json = workspaceRepo?.exportJson(ws) ?: ""
                                    println("=== Workspace Export: ${ws.name} ===\n$json\n=== END ===")
                                }
                            },
                            onDuplicate = { ws ->
                                scope.launch {
                                    val copy = ws.copy(id = com.aandios.nous.core.workspace.generateId(), name = "${ws.name} (copy)")
                                    workspaceRepo?.create(copy); refreshWorkspaces()
                                }
                            },
                            onSaveAsTemplate = templateRepo?.let { repo ->
                                { ws ->
                                    scope.launch {
                                        repo.create(ws.copy(id = com.aandios.nous.core.workspace.generateId()))
                                        workspaceBus?.workspaceChanged()
                                    }
                                }
                            },
                            modifier = Modifier.width(topPanelState.width).fillMaxHeight()
                        )
                    } else {
                        ToolDetailsPanel(
                            type = topPanelState.type ?: ToolPanelType.SYMBOLS,
                            width = topPanelState.width,
                            selectedSymbol = selectedSymbol,
                            onSymbolSelected = onSymbolSelected,
                            selectedTimeframe = selectedTimeframe,
                            onTimeframeSelected = onTimeframeSelected,
                            onClose = { topPanelState = topPanelState.copy(isExpanded = false) },
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                    // Draggable right border
                    Box(
                        modifier = Modifier
                            .width(4.dp).fillMaxHeight()
                            .background(if (sidebarHovered) Color(0xFF00C853).copy(alpha = 0.4f) else Color(0xFF333333))
                            .hoverable(sidebarInteractionSource)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    topPanelState = topPanelState.copy(
                                        width = (topPanelState.width + dragAmount.dp).coerceIn(140.dp, 500.dp)
                                    )
                                }
                            }
                    )
                }
            }

            // Контент окна + нижняя выдвижная панель
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { contentAreaHeightPx = it.height.toFloat() }
            ) {
                mainContent()
                if (bottomPanelState != null) {
                    val density = LocalDensity.current
                    val minHeight = 120.dp
                    val maxHeight = with(density) {
                        (contentAreaHeightPx * 0.75f).toDp()
                    }.coerceAtLeast(minHeight)

                    BottomResizeHandle(
                        onDrag = { dy ->
                            bottomPanelHeight = (bottomPanelHeight - with(density) { dy.toDp() })
                                .coerceIn(minHeight, maxHeight)
                        }
                    )
                    BottomToolPanel(
                        type = bottomPanelState!!,
                        onClose = { bottomPanelState = null },
                        portfolioTab = portfolioTab,
                        onPortfolioTabChange = { portfolioTab = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bottomPanelHeight)
                    )
                }
            }
        }
    }
}
