/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.component.TerminalGripDots
import com.aandios.nous.core.ui.component.TerminalGripOrientation
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
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
    val width: Dp
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
    marketWatchTemplates: List<Pair<String, WorkspaceConfig>> = emptyList(),
    onOpenMarketWatchTemplate: ((WorkspaceConfig, String, String, String, String) -> Unit)? = null,
    onOpenThemeSettings: () -> Unit,
    mainContent: @Composable ColumnScope.() -> Unit,
) {
    val designTokens = LocalTerminalDesignTokens.current
    var topPanelState by remember {
        mutableStateOf(
            ToolPanelState(
                isExpanded = true,
                type = ToolPanelType.SYMBOLS,
                width = designTokens.dimensions.workspaceToolPanelWidth,
            )
        )
    }
    var bottomPanelState by remember { mutableStateOf<BottomToolType?>(null) }
    var portfolioTab by remember { mutableStateOf(PortfolioTab.POSITIONS) }
    // Высота нижней панели запоминается на время сессии
    var bottomPanelHeight by remember {
        mutableStateOf(designTokens.dimensions.workspaceBottomPanelInitialHeight)
    }
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
            .background(designTokens.colors.workspaceBackground)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Основная панель с иконками (всегда видима)
            Column(
                modifier = Modifier
                    .padding(designTokens.dimensions.workspacePanelGap / 2)
                    .width(designTokens.dimensions.workspaceSidebarWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(designTokens.cornerRadii.workspacePanel))
                    .background(designTokens.colors.panelSurface)
                    .border(
                        designTokens.dimensions.borderWidth,
                        designTokens.colors.panelBorder,
                        RoundedCornerShape(designTokens.cornerRadii.workspacePanel),
                    )
            ) {
                // Верхняя группа иконок
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = designTokens.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(designTokens.spacing.md),
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
                        .padding(horizontal = designTokens.spacing.md)
                        .fillMaxWidth()
                        .height(designTokens.dimensions.dividerThickness)
                        .background(designTokens.colors.panelBorder)
                )

                // Нижняя группа иконок
                Column(
                    modifier = Modifier.padding(vertical = designTokens.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(designTokens.spacing.md),
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
                    ToolBarIcon(
                        icon = Res.drawable.settings,
                        description = "Theme settings",
                        isSelected = false,
                        onClick = onOpenThemeSettings,
                    )
                }
            }

            // Верхняя выдвижная панель (слева, под иконками)
            if (topPanelState.isExpanded) {
                // Draggable handle state
                val sidebarInteractionSource = remember { MutableInteractionSource() }
                val sidebarHovered by sidebarInteractionSource.collectIsHoveredAsState()
                Row {
                    if (topPanelState.type == ToolPanelType.WORKSPACES && useWorkspaces) {
                        Box(
                            modifier = Modifier
                                .padding(designTokens.dimensions.workspacePanelGap / 2)
                                .width(topPanelState.width)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(designTokens.cornerRadii.workspacePanel))
                                .background(designTokens.colors.panelSurface)
                                .border(
                                    designTokens.dimensions.borderWidth,
                                    designTokens.colors.panelBorder,
                                    RoundedCornerShape(designTokens.cornerRadii.workspacePanel),
                                )
                        ) {
                            ProjectTree(
                                workspaces = allWorkspaceConfigs,
                                activeId = activeWorkspaceId,
                                onWorkspaceClick = { config -> onOpenWorkspace?.invoke(config) },
                                onNewWorkspace = {
                                    scope.launch {
                                        val config = com.aandios.nous.core.workspace.Templates.nous()
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
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        ToolDetailsPanel(
                            type = topPanelState.type ?: ToolPanelType.SYMBOLS,
                            width = topPanelState.width,
                            selectedSymbol = selectedSymbol,
                            onSymbolSelected = onSymbolSelected,
                            selectedTimeframe = selectedTimeframe,
                            onTimeframeSelected = onTimeframeSelected,
                            onClose = { topPanelState = topPanelState.copy(isExpanded = false) },
                            marketWatchTemplates = marketWatchTemplates,
                            onOpenMarketWatchTemplate = onOpenMarketWatchTemplate,
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                    // Draggable right border
                    Box(
                        modifier = Modifier
                            .width(designTokens.dimensions.splitHandleSize).fillMaxHeight()
                            .hoverable(sidebarInteractionSource)
                            .testTag("sidebar-resize-handle")
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    topPanelState = topPanelState.copy(
                                        width = (topPanelState.width + dragAmount.dp).coerceIn(
                                            designTokens.dimensions.workspaceToolPanelMinWidth,
                                            designTokens.dimensions.workspaceToolPanelMaxWidth
                                        )
                                    )
                                }
                            }
                    ) {
                        TerminalGripDots(
                            orientation = TerminalGripOrientation.VERTICAL,
                            color = if (sidebarHovered) designTokens.colors.accent
                            else designTokens.colors.label.copy(alpha = 0.75f),
                        )
                    }
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
                    val minHeight = designTokens.dimensions.workspaceBottomPanelMinHeight
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
