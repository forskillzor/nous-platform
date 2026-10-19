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
import com.aandios.nous.core.ui.workspace.WorkspaceNavigator
import com.aandios.nous.core.ui.workspace.applyWorkspaceDrop
import com.aandios.nous.core.workspace.NavigatorState
import com.aandios.nous.core.workspace.NavigatorStateRepository
import com.aandios.nous.core.workspace.TemplateRepository
import com.aandios.nous.core.workspace.WorkspaceBus
import com.aandios.nous.core.workspace.WorkspaceConfig
import com.aandios.nous.core.workspace.WorkspaceRepository
import com.aandios.nous.core.workspace.duplicateWorkspaceConfig
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
    topPanelState: ToolPanelState,
    onTopPanelStateChange: (ToolPanelState) -> Unit,
    onTopPanelWidthDelta: ((Dp) -> Unit)? = null,
    onSymbolSelected: (String, String) -> Unit,
    onTimeframeSelected: (String) -> Unit,
    tabManager: TabManager? = null,
    workspaceRepo: WorkspaceRepository? = null,
    templateRepo: TemplateRepository? = null,
    workspaceBus: WorkspaceBus? = null,
    navigatorStateRepo: NavigatorStateRepository? = null,
    workspaceTemplates: List<Pair<String, WorkspaceConfig>> = emptyList(),
    activeWorkspaceId: String? = null,
    onOpenWorkspace: ((WorkspaceConfig) -> Unit)? = null,
    onPreviewWorkspace: ((WorkspaceConfig) -> Unit)? = null,
    marketWatchTemplates: List<Pair<String, WorkspaceConfig>> = emptyList(),
    onOpenMarketWatchTemplate: ((WorkspaceConfig, String, String, String, String) -> Unit)? = null,
    onApplyToCurrentWorkspace: ((String, String, String) -> Unit)? = null,
    onOpenThemeSettings: () -> Unit,
    mainContent: @Composable ColumnScope.() -> Unit,
) {
    val designTokens = LocalTerminalDesignTokens.current
    val density = LocalDensity.current
    var bottomPanelState by remember { mutableStateOf<BottomToolType?>(null) }
    var portfolioTab by remember { mutableStateOf(PortfolioTab.POSITIONS) }
    // Высота нижней панели запоминается на время сессии
    var bottomPanelHeight by remember {
        mutableStateOf(designTokens.dimensions.workspaceBottomPanelInitialHeight)
    }
    var contentAreaHeightPx by remember { mutableFloatStateOf(0f) }

    // Реактивные значения из VM (раньше читались один раз через remember)
    val selectedSymbol by terminalState.selectedSymbol.collectAsState()
    val selectedProviderId by terminalState.selectedProviderId.collectAsState()
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

    // Состояние навигатора (порядок/состав папок, свёрнутые): персистится отдельно.
    var navigatorState by remember { mutableStateOf(NavigatorState()) }
    if (useWorkspaces) {
        LaunchedEffect(Unit) { navigatorState = navigatorStateRepo?.load() ?: NavigatorState() }
    }

    fun updateNavigatorState(next: NavigatorState) {
        navigatorState = next
        scope.launch { navigatorStateRepo?.save(next) }
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
                    .padding(
                        start = designTokens.dimensions.workspacePanelGap,
                        top = designTokens.dimensions.workspacePanelGap,
                        bottom = designTokens.dimensions.workspacePanelGap,
                    )
                    .width(designTokens.dimensions.workspaceSidebarWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(designTokens.cornerRadii.workspacePanel))
                    .background(designTokens.colors.panelSurface)
                    .border(
                        designTokens.dimensions.borderWidth,
                        designTokens.colors.panelBorder,
                        RoundedCornerShape(designTokens.cornerRadii.workspacePanel),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
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
                            onClick = { onTopPanelStateChange(topPanelState.toggle(type)) }
                        )
                    }
                }

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
                Box(modifier = Modifier.fillMaxHeight()) {
                    if (topPanelState.type == ToolPanelType.WORKSPACES && useWorkspaces) {
                        Box(
                            modifier = Modifier
                                .padding(
                                    start = designTokens.dimensions.workspacePanelGap,
                                    top = designTokens.dimensions.workspacePanelGap,
                                    bottom = designTokens.dimensions.workspacePanelGap,
                                )
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
                            WorkspaceNavigator(
                                workspaces = allWorkspaceConfigs,
                                activeId = activeWorkspaceId,
                                folderOrder = navigatorState.folders,
                                collapsedFolders = navigatorState.collapsed.toSet(),
                                templates = workspaceTemplates,
                                onWorkspacePreview = { config -> onPreviewWorkspace?.invoke(config) },
                                onWorkspaceOpen = { config -> onOpenWorkspace?.invoke(config) },
                                onNewWorkspace = {
                                    scope.launch {
                                        val config = com.aandios.nous.core.workspace.Templates.nous()
                                        workspaceRepo!!.create(config)
                                        onOpenWorkspace?.invoke(config)
                                        refreshWorkspaces()
                                    }
                                },
                                onCreateFolder = { name ->
                                    updateNavigatorState(
                                        navigatorState.copy(folders = (navigatorState.folders + name).distinct()),
                                    )
                                },
                                onRenameFolder = { oldName, newName ->
                                    scope.launch {
                                        workspaceRepo?.getAll()
                                            ?.filter { it.group == oldName }
                                            ?.forEach { workspaceRepo?.update(it.copy(group = newName), touchUpdatedAt = false) }
                                        val folders = navigatorState.folders.map { if (it == oldName) newName else it }.distinct()
                                        val collapsed = navigatorState.collapsed.toMutableList().apply {
                                            if (remove(oldName)) add(newName)
                                        }
                                        updateNavigatorState(navigatorState.copy(folders = folders, collapsed = collapsed))
                                        refreshWorkspaces()
                                    }
                                },
                                onDeleteFolder = { name ->
                                    scope.launch {
                                        workspaceRepo?.getAll()
                                            ?.filter { it.group == name }
                                            ?.forEach { workspaceRepo?.update(it.copy(group = ""), touchUpdatedAt = false) }
                                        updateNavigatorState(
                                            navigatorState.copy(
                                                folders = navigatorState.folders - name,
                                                collapsed = navigatorState.collapsed - name,
                                            ),
                                        )
                                        refreshWorkspaces()
                                    }
                                },
                                onRenameWorkspace = { ws, newName ->
                                    scope.launch { workspaceRepo?.update(ws.copy(name = newName)); refreshWorkspaces() }
                                },
                                onDeleteWorkspace = { ws ->
                                    scope.launch {
                                        tabManager?.closeWorkspace(ws.id)
                                        workspaceRepo?.delete(ws.id)
                                        refreshWorkspaces()
                                    }
                                },
                                onMoveWorkspace = { ws, targetFolder, beforeId ->
                                    scope.launch {
                                        val all = workspaceRepo?.getAll() ?: emptyList()
                                        val before = all.associateBy { it.id }
                                        applyWorkspaceDrop(all, ws.id, targetFolder, beforeId).forEach { updated ->
                                            if (before[updated.id] != updated) {
                                                workspaceRepo?.update(updated, touchUpdatedAt = false)
                                            }
                                        }
                                        refreshWorkspaces()
                                    }
                                },
                                onCollapsedFoldersChange = { collapsed ->
                                    updateNavigatorState(navigatorState.copy(collapsed = collapsed.toList()))
                                },
                                onFolderOrderChange = { order ->
                                    updateNavigatorState(navigatorState.copy(folders = order))
                                },
                                onCreateFromTemplate = { _, template ->
                                    scope.launch {
                                        val fresh = template.copy(
                                            id = com.aandios.nous.core.workspace.generateId(),
                                            createdAt = com.aandios.nous.core.workspace.currentTime(),
                                            updatedAt = com.aandios.nous.core.workspace.currentTime(),
                                        )
                                        workspaceRepo?.create(fresh)
                                        onOpenWorkspace?.invoke(fresh)
                                        refreshWorkspaces()
                                    }
                                },
                                onMakeTemplate = templateRepo?.let { repo ->
                                    { ws: WorkspaceConfig ->
                                        scope.launch {
                                            repo.create(ws.copy(id = com.aandios.nous.core.workspace.generateId()))
                                            workspaceBus?.workspaceChanged()
                                        }
                                    }
                                },
                                onDuplicateWorkspace = { ws ->
                                    scope.launch {
                                        val copy = duplicateWorkspaceConfig(ws)
                                        workspaceRepo?.create(copy)
                                        onOpenWorkspace?.invoke(copy)
                                        refreshWorkspaces()
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
                            selectedProviderId = selectedProviderId,
                            onSymbolSelected = onSymbolSelected,
                            selectedTimeframe = selectedTimeframe,
                            onTimeframeSelected = onTimeframeSelected,
                            onClose = { onTopPanelStateChange(topPanelState.copy(isExpanded = false)) },
                            marketWatchTemplates = marketWatchTemplates,
                            onOpenMarketWatchTemplate = onOpenMarketWatchTemplate,
                            onApplyToCurrentWorkspace = onApplyToCurrentWorkspace,
                            onWidthDelta = onTopPanelWidthDelta?.let { applyDelta ->
                                { dragAmountPx -> applyDelta(with(density) { dragAmountPx.toDp() }) }
                            },
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                    // Ручка ширины панели: оверлей НА краю островка (не занимает
                    // layout-ширину, чтобы зазоры оставались равными везде).
                    // Зона захвата — 8dp вокруг края, грип-точки по центру.
                    // Дельту отдаём наружу (onTopPanelWidthDelta): ширина живёт в
                    // topPanelState родителя, а значение-параметр в pointerInput(Unit)
                    // замыкается на старую композицию — обработка state здесь давала
                    // «дрожание» вместо ресайза (жест каждый раз шёл от устаревшей ширины).
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = designTokens.dimensions.workspacePanelGap / 2)
                            .width(designTokens.dimensions.workspacePanelGap)
                            .fillMaxHeight()
                            .hoverable(sidebarInteractionSource)
                            .testTag("sidebar-resize-handle")
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    onTopPanelWidthDelta?.invoke(with(density) { dragAmount.toDp() })
                                }
                            }
                    ) {
                        TerminalGripDots(
                            orientation = TerminalGripOrientation.VERTICAL,
                            color = if (sidebarHovered) designTokens.colors.accent
                            else designTokens.colors.label.copy(alpha = 0.5f),
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
                            .padding(
                                start = designTokens.dimensions.workspacePanelGap,
                                end = designTokens.dimensions.workspacePanelGap,
                                bottom = designTokens.dimensions.workspacePanelGap,
                            )
                    )
                }
            }
        }
    }
}
