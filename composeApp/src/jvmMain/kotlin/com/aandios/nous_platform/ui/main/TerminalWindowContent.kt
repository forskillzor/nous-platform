/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.v2.WindowState
import com.aandios.nous.core.ui.workspace.TabBar
import com.aandios.nous.core.ui.workspace.WelcomeScreen
import com.aandios.nous.core.workspace.*
import com.aandios.nous.core.workspace.viewmodel.TabManager
import com.aandios.nous.core.workspace.viewmodel.WindowManager
import com.aandios.nous.core.ui.component.LocalSymbolWatchlistRepository
import com.aandios.nous.core.ui.component.SymbolWatchlistRepository
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.feature.trading.ui.PaperModeState
import com.aandios.nous_platform.ui.terminalLayout.TerminalLayout
import com.aandios.nous_platform.ui.terminalLayout.MarketWatchStore
import com.aandios.nous_platform.ui.terminalLayout.TerminalStateViewModel
import com.aandios.nous_platform.ui.terminalLayout.ThemeSettingsOverlay
import com.aandios.nous_platform.ui.terminalLayout.ToolPanelState
import com.aandios.nous_platform.ui.terminalLayout.ToolPanelType
import com.aandios.nous_platform.ui.terminalLayout.WorkspaceTemplatePicker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import com.aandios.nous_platform.ui.terminalLayout.TerminalMode
import com.aandios.nous_platform.ui.terminalLayout.TerminalModeBar
import com.aandios.nous_platform.ui.terminalLayout.TerminalTopBar
import org.koin.compose.koinInject

/**
 * Содержимое одного окна терминала: сессия вкладок, TabBar, активный workspace
 * или WelcomeScreen. Общее для всех окон (multi-window как в браузере).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TerminalWindowContent(
    session: WindowSessionState,
    windowState: WindowState,
    tabManager: TabManager,
    workspaceRepo: WorkspaceRepository,
    templateRepo: TemplateRepository,
    workspaceBus: WorkspaceBus,
    windowManager: WindowManager,
    dragBus: TabDragBus,
    terminalStateViewModel: TerminalStateViewModel,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val symbolWatchlists: SymbolWatchlistRepository = koinInject()
    LaunchedEffect(symbolWatchlists) { symbolWatchlists.load() }
    val marketWatchStore: MarketWatchStore = koinInject()
    LaunchedEffect(marketWatchStore) { marketWatchStore.start() }
    val paperModeState: PaperModeState = koinInject()
    val paperEnabled by paperModeState.enabled.collectAsState()

    // Публикуем границы окна (px) для hit-test переноса вкладок + персистим геометрию
    LaunchedEffect(session.id, windowState) {
        snapshotFlow {
            // v2 WindowState бросает исключение при чтении size/position до
            // инициализации окна — ждём isInitialized и реагируем на него.
            if (windowState.isInitialized) windowState.size to windowState.position else null
        }.collectLatest { bounds ->
            if (bounds == null) return@collectLatest
            val size = bounds.first
            val pos = bounds.second
            val x = if (pos.isSpecified) with(density) { pos.x.roundToPx() } else null
            val y = if (pos.isSpecified) with(density) { pos.y.roundToPx() } else null
            val w = with(density) { size.width.roundToPx() }
            val h = with(density) { size.height.roundToPx() }
            if (x != null && y != null) {
                WindowBoundsRegistry.update(session.id, WindowBoundsRegistry.Bounds(x, y, w, h))
            }
            windowManager.updateSessionGeometry(session.id, x, y, w, h)
        }
    }
    DisposableEffect(session.id) {
        onDispose { WindowBoundsRegistry.remove(session.id) }
    }

    val workspaces by tabManager.workspaces.collectAsState()
    val windowWorkspaces = remember(workspaces, session.workspaceIds) {
        session.workspaceIds.mapNotNull { id -> workspaces.find { it.config.id == id } }
    }
    val activeIdx = windowWorkspaces.indexOfFirst { it.config.id == session.activeWorkspaceId }.coerceAtLeast(0)
    val activeWorkspace = windowWorkspaces.getOrNull(activeIdx)

    // Недавние / шаблоны — реактивно через WorkspaceBus
    var recentConfigs by remember { mutableStateOf<List<WorkspaceConfig>>(emptyList()) }
    var userTemplates by remember { mutableStateOf<List<WorkspaceConfig>>(emptyList()) }
    var hiddenBuiltins by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        workspaceBus.version.collect {
            recentConfigs = workspaceRepo.getAll()
                .sortedByDescending { it.updatedAt }
                .take(8)
            userTemplates = templateRepo.getAll()
            hiddenBuiltins = templateRepo.hiddenBuiltins()
        }
    }
    val marketWatchTemplates = remember(userTemplates, hiddenBuiltins) {
        builtinWorkspaceTemplates()
            .filter { it.id !in hiddenBuiltins }
            .map { it.title to it.preview } +
                userTemplates.map { it.name to it }
    }

    // Перенос вкладки между окнами
    var dragPayload by remember { mutableStateOf<TabDragBus.Payload?>(null) }
    val externalDrag by dragBus.active.collectAsState()
    val externalDragActive = externalDrag?.let { it.sourceWindowId != session.id } == true
    var themeSettingsOpen by remember { mutableStateOf(false) }

    var currentMode by remember { mutableStateOf(TerminalMode.ACTIVE_FLOW) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    val tokens = LocalTerminalDesignTokens.current

    // Верхняя выдвижная панель (Market Watch и т.д.): состояние поднято из
    // TerminalLayout, чтобы «MANAGE WATCHLIST» из explorer могла её открыть
    var topPanelState by remember {
        mutableStateOf(
            ToolPanelState(
                isExpanded = true,
                type = ToolPanelType.SYMBOLS,
                width = tokens.dimensions.workspaceToolPanelWidth,
            )
        )
    }

    CompositionLocalProvider(LocalSymbolWatchlistRepository provides symbolWatchlists) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // ── TopBar с TabBar и кнопкой «+» ──
                TerminalTopBar(
                    tabsSlot = {
                        TabBar(
                            workspaces = windowWorkspaces,
                            activeIndex = activeIdx,
                            onTabClick = { i -> windowManager.activate(session.id, windowWorkspaces[i].config.id) },
                            onTabClose = { id -> windowManager.closeWorkspaceIn(session.id, id) },
                            onTabReorder = { from, to -> windowManager.reorderWorkspace(session.id, from, to) },
                            onDragStart = { wsId ->
                                dragPayload = TabDragBus.Payload(session.id, wsId)
                                dragBus.start(dragPayload!!)
                            },
                            onDragEndResolve = {
                                dragBus.end()
                                val payload = dragPayload
                                dragPayload = null
                                if (payload != null) {
                                    val cursor = WindowBoundsRegistry.cursorLocation()
                                    if (cursor != null) {
                                        val targetId = WindowBoundsRegistry.boundsAt(cursor.first, cursor.second)
                                        val sourceBounds = WindowBoundsRegistry.bounds.value[payload.sourceWindowId]
                                        val outsideSource = sourceBounds == null ||
                                                !sourceBounds.contains(cursor.first, cursor.second)
                                        when {
                                            targetId != null && targetId != payload.sourceWindowId ->
                                                windowManager.moveWorkspace(
                                                    payload.workspaceId,
                                                    payload.sourceWindowId,
                                                    targetId,
                                                    Int.MAX_VALUE
                                                )

                                            targetId == null && outsideSource ->
                                                // Отпустили вне всех окон — detach в новое окно у курсора
                                                windowManager.detachWorkspace(
                                                    payload.workspaceId,
                                                    payload.sourceWindowId,
                                                    cursor.first,
                                                    cursor.second
                                                )
                                        }
                                    }
                                }
                            },
                            onOpenInNewWindow = { wsId ->
                                val cursor = WindowBoundsRegistry.cursorLocation()
                                windowManager.detachWorkspace(wsId, session.id, cursor?.first, cursor?.second)
                            },
                            externalDragActive = externalDragActive,
                            trailing = {
                                Box {
                                    Text(
                                        text = "+",
                                        color = tokens.colors.label,
                                        fontSize = tokens.typography.compactActionSize,
                                        fontFamily = TerminalFontFamily,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                                            .clickable { addMenuExpanded = true }
                                            .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
                                    )
                                    DropdownMenu(
                                        expanded = addMenuExpanded,
                                        onDismissRequest = { addMenuExpanded = false },
                                    ) {
                                        WorkspaceTemplatePicker(
                                            title = "NEW WORKSPACE",
                                            templates = marketWatchTemplates,
                                            onPick = { _, template ->
                                                addMenuExpanded = false
                                                scope.launch {
                                                    val fresh = template.copy(
                                                        id = generateId(),
                                                        createdAt = currentTime(),
                                                        updatedAt = currentTime(),
                                                    )
                                                    workspaceRepo.create(fresh)
                                                    windowManager.openWorkspaceIn(session.id, fresh)
                                                    workspaceBus.workspaceChanged()
                                                }
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    },
                    paperEnabled = paperEnabled,
                    onPaperToggle = { paperModeState.set(it) },
                    onThemeClick = { themeSettingsOpen = true },
                    onSettingsClick = { /* TODO: окно настроек */ },
                )

                // ── ModeBar ──
                TerminalModeBar(
                    currentMode = currentMode,
                    onModeChange = { currentMode = it },
                )

                TerminalLayout(
                    modifier = Modifier.fillMaxWidth()
                        .weight(1f),
                    terminalState = terminalStateViewModel,
                    topPanelState = topPanelState,
                    onTopPanelStateChange = { topPanelState = it },
                    tabManager = tabManager,
                    workspaceRepo = workspaceRepo,
                    templateRepo = templateRepo,
                    workspaceBus = workspaceBus,
                    activeWorkspaceId = session.activeWorkspaceId,
                    onOpenWorkspace = { config -> windowManager.openWorkspaceIn(session.id, config) },
                    onSymbolSelected = { terminalStateViewModel.changeSymbol(it) },
                    onTimeframeSelected = { terminalStateViewModel.changeTimeFrame(it) },
                    marketWatchTemplates = marketWatchTemplates,
                    onOpenThemeSettings = { themeSettingsOpen = true },
                    onOpenMarketWatchTemplate = { template, templateName, symbol, providerId, providerName ->
                        scope.launch {
                            val fresh = template.forMarketWatchSymbol(
                                symbol = symbol,
                                providerId = providerId,
                                providerName = providerName,
                                templateName = templateName,
                            )
                            workspaceRepo.create(fresh)
                            windowManager.openWorkspaceIn(session.id, fresh)
                            workspaceBus.workspaceChanged()
                        }
                    },
                    onApplyToCurrentWorkspace = activeWorkspace?.let { ws ->
                        val apply: (String, String, String) -> Unit = { symbol, providerId, providerName ->
                            scope.launch {
                                val updated = ws.config.withMarketWatchSymbol(
                                    symbol = symbol,
                                    providerId = providerId,
                                    providerName = providerName,
                                )
                                ws.updateConfig(updated)
                                workspaceRepo.update(updated)
                                workspaceBus.workspaceChanged()
                            }
                        }
                        apply
                    },
                ) {
                    if (windowWorkspaces.isEmpty()) {
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
                                    windowManager.openWorkspaceIn(session.id, fresh)
                                    workspaceBus.workspaceChanged()
                                }
                            },
                            onOpenRecent = { config -> windowManager.openWorkspaceInSession(session.id, config.id) },
                            onDeleteTemplate = { template ->
                                scope.launch {
                                    templateRepo.delete(template.id)
                                    workspaceBus.workspaceChanged()
                                }
                            },
                            onRenameTemplate = { template, newName ->
                                scope.launch {
                                    templateRepo.create(template.copy(name = newName.ifBlank { template.name }))
                                    workspaceBus.workspaceChanged()
                                }
                            },
                            onDuplicateTemplate = { template ->
                                scope.launch {
                                    templateRepo.create(
                                        template.copy(
                                            id = generateId(),
                                            name = "${template.name} (copy)"
                                        )
                                    )
                                    workspaceBus.workspaceChanged()
                                }
                            },
                            onChangeTemplateDescription = { template, description ->
                                scope.launch {
                                    templateRepo.create(template.copy(description = description))
                                    workspaceBus.workspaceChanged()
                                }
                            },
                            onHideBuiltinTemplate = { builtinId ->
                                scope.launch {
                                    templateRepo.hideBuiltin(builtinId)
                                    workspaceBus.workspaceChanged()
                                }
                            },
                            onDeleteRecent = { config ->
                                scope.launch {
                                    tabManager.closeWorkspace(config.id)
                                    workspaceRepo.delete(config.id)
                                    workspaceBus.workspaceChanged()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            activeWorkspace?.let { ws ->
                                WorkspaceView(
                                    ws = ws,
                                    workspaceRepo = workspaceRepo,
                                    onManageWatchlist = {
                                        // Уже открыт — не сворачиваем, это «manage»-действие
                                        if (!(topPanelState.type == ToolPanelType.SYMBOLS &&
                                                topPanelState.isExpanded)
                                        ) {
                                            topPanelState = topPanelState.copy(
                                                isExpanded = true,
                                                type = ToolPanelType.SYMBOLS,
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
            if (themeSettingsOpen) {
                ThemeSettingsOverlay(onClose = { themeSettingsOpen = false })
            }
        }
    }
}
