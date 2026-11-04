/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.v2.WindowState
import com.aandios.nous.core.ui.workspace.TabBar
import com.aandios.nous.core.ui.workspace.WelcomeScreen
import com.aandios.nous.core.workspace.*
import com.aandios.nous.core.workspace.viewmodel.TabManager
import com.aandios.nous.core.workspace.viewmodel.WindowManager
import com.aandios.nous.core.ui.component.LocalSymbolWatchlistRepository
import com.aandios.nous.core.ui.component.SymbolWatchlistRepository
import com.aandios.nous.core.ui.component.TerminalDropdownMenu
import com.aandios.nous.core.ui.panel.ToolPanelPrefsRepository
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.feature.trading.ui.PaperModeState
import com.aandios.nous_platform.ui.terminalLayout.TerminalLayout
import com.aandios.nous_platform.ui.terminalLayout.TerminalStateViewModel
import com.aandios.nous_platform.ui.terminalLayout.ThemeSettingsOverlay
import com.aandios.nous_platform.ui.terminalLayout.ToolPanelState
import com.aandios.nous_platform.ui.terminalLayout.ToolPanelType
import com.aandios.nous_platform.ui.terminalLayout.WorkspaceTemplatePicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import com.aandios.nous_platform.ui.terminalLayout.TerminalMode
import com.aandios.nous_platform.ui.terminalLayout.TerminalModeBar
import com.aandios.nous_platform.ui.terminalLayout.TerminalTopBar
import org.koin.compose.koinInject
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import kotlin.math.roundToInt

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
    val navigatorStateRepository: NavigatorStateRepository = koinInject()
    val toolPanelPrefsRepository: ToolPanelPrefsRepository = koinInject()
    val paperModeState: PaperModeState = koinInject()
    val paperEnabled by paperModeState.enabled.collectAsState()

    // Геометрия состояния окна нужна только для персистенса; drag hit-test
    // использует screen coordinates, измеренные непосредственно в Compose layout.
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
            windowManager.updateSessionGeometry(session.id, x, y, w, h)
        }
    }
    DisposableEffect(session.id) {
        onDispose { WindowBoundsRegistry.remove(session.id) }
    }

    DisposableEffect(session.id, windowManager, dragBus) {
        val listener = AWTEventListener { event ->
            if (event is MouseEvent && event.id == MouseEvent.MOUSE_RELEASED &&
                event.button == MouseEvent.BUTTON1
            ) {
                val payload = dragBus.active.value?.takeIf { it.sourceWindowId == session.id }
                val cursor = WindowBoundsRegistry.cursorLocation()
                if (payload != null && cursor != null) {
                    val targetWindow = WindowBoundsRegistry.boundsAt(cursor.first, cursor.second)
                    if (targetWindow == session.id && dragBus.wasCancelled(payload)) {
                        dragBus.end(payload)
                    } else if (targetWindow != session.id) {
                        if (dragBus.end(payload)) {
                            resolveTabDragDrop(payload, windowManager)
                        }
                    }
                }
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)
        onDispose {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        }
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
    val externalDrag by dragBus.active.collectAsState()
    val externalDragActive = externalDrag?.let { it.sourceWindowId != session.id } == true
    var themeSettingsOpen by remember { mutableStateOf(false) }

    var currentMode by remember { mutableStateOf(TerminalMode.WORKSPACES) }
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
    // Персистенс ширины сайдбара: грузим до первого сохранения, пишем с дебаунсом.
    var toolPanelWidthLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(toolPanelPrefsRepository) {
        val restored = toolPanelPrefsRepository
            .loadWidth(tokens.dimensions.workspaceToolPanelWidth.value)
            .dp
            .coerceIn(
                tokens.dimensions.workspaceToolPanelMinWidth,
                tokens.dimensions.workspaceToolPanelMaxWidth,
            )
        topPanelState = topPanelState.copy(width = restored)
        toolPanelWidthLoaded = true
    }
    LaunchedEffect(topPanelState.width, toolPanelWidthLoaded) {
        if (!toolPanelWidthLoaded) return@LaunchedEffect
        delay(400)
        toolPanelPrefsRepository.saveWidth(topPanelState.width.value)
    }

    CompositionLocalProvider(LocalSymbolWatchlistRepository provides symbolWatchlists) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val origin = coordinates.positionOnScreen()
                    if (!origin.x.isFinite() || !origin.y.isFinite()) return@onGloballyPositioned
                    WindowBoundsRegistry.update(
                        session.id,
                        WindowBoundsRegistry.Bounds(
                            origin.x.roundToInt() - 8,
                            origin.y.roundToInt() - 48,
                            coordinates.size.width + 16,
                            coordinates.size.height + 56,
                        ),
                    )
                }
        ) {
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
                                val payload = TabDragBus.Payload(session.id, wsId)
                                dragBus.start(payload)
                            },
                            onDragEndResolve = {
                                val payload = dragBus.active.value?.takeIf { it.sourceWindowId == session.id }
                                payload != null && dragBus.end(payload) &&
                                    resolveTabDragDrop(payload, windowManager)
                            },
                            onDragCancel = {
                                dragBus.active.value
                                    ?.takeIf { it.sourceWindowId == session.id }
                                    ?.let(dragBus::markCancelled)
                            },
                            onOpenInNewWindow = { wsId ->
                                val cursor = WindowBoundsRegistry.cursorLocation()
                                windowManager.detachWorkspace(wsId, session.id, cursor?.first, cursor?.second)
                            },
                            previewWorkspaceId = session.previewWorkspaceId,
                            onTabDoubleClick = { wsId -> windowManager.promotePreview(session.id, wsId) },
                            externalDragActive = externalDragActive,
                            onTabBarBoundsChanged = { x, y, width, height ->
                                WindowBoundsRegistry.updateTabBarBounds(
                                    session.id,
                                    WindowBoundsRegistry.Bounds(x, y, width, height),
                                )
                            },
                            onTabWorkspaceIdsChanged = { ids ->
                                WindowBoundsRegistry.updateTabWorkspaceIds(session.id, ids)
                            },
                            onTabCenterChanged = { workspaceId, centerX ->
                                WindowBoundsRegistry.updateTabCenter(session.id, workspaceId, centerX)
                            },
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
                                    TerminalDropdownMenu(
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
                    // Ресайз ширины сайдбара: read-modify-write по живому состоянию
                    // (жест в pointerInput(Unit) не должен строиться от устаревшего
                    // значения topPanelState из первой композиции — иначе дрожание).
                    onTopPanelWidthDelta = { delta ->
                        topPanelState = topPanelState.copy(
                            width = (topPanelState.width + delta).coerceIn(
                                tokens.dimensions.workspaceToolPanelMinWidth,
                                tokens.dimensions.workspaceToolPanelMaxWidth
                            )
                        )
                    },
                    tabManager = tabManager,
                    workspaceRepo = workspaceRepo,
                    templateRepo = templateRepo,
                    workspaceBus = workspaceBus,
                    navigatorStateRepo = navigatorStateRepository,
                    workspaceTemplates = marketWatchTemplates,
                    activeWorkspaceId = session.activeWorkspaceId,
                    previewWorkspaceId = session.previewWorkspaceId,
                    onOpenWorkspace = { config -> windowManager.openWorkspaceIn(session.id, config) },
                    onPreviewWorkspace = { config -> windowManager.openWorkspacePreviewIn(session.id, config) },
                    onSymbolSelected = { symbol, providerId ->
                        terminalStateViewModel.changeSymbol(symbol, providerId)
                    },
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

private fun resolveTabDragDrop(
    payload: TabDragBus.Payload,
    windowManager: WindowManager,
): Boolean {
    val cursor = WindowBoundsRegistry.cursorLocation() ?: return true
    val targetTabBar = WindowBoundsRegistry.tabBarAt(cursor.first, cursor.second)
    if (targetTabBar == payload.sourceWindowId) return true
    if (targetTabBar != null) {
        windowManager.moveWorkspace(
            workspaceId = payload.workspaceId,
            fromSessionId = payload.sourceWindowId,
            toSessionId = targetTabBar,
            toIndex = WindowBoundsRegistry.tabInsertionIndex(targetTabBar, cursor.first),
        )
        return false
    }

    val targetWindow = WindowBoundsRegistry.boundsAt(cursor.first, cursor.second)
    if (targetWindow != null) return false

    val sourceBounds = WindowBoundsRegistry.bounds.value[payload.sourceWindowId] ?: return false
    if (!sourceBounds.contains(cursor.first, cursor.second)) {
        windowManager.detachWorkspace(
            payload.workspaceId,
            payload.sourceWindowId,
            cursor.first,
            cursor.second,
        )
    }
    return false
}
