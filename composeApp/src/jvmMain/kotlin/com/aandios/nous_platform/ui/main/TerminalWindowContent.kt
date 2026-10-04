/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.v2.WindowState
import com.aandios.nous.core.ui.workspace.TabBar
import com.aandios.nous.core.ui.workspace.WelcomeScreen
import com.aandios.nous.core.workspace.*
import com.aandios.nous.core.workspace.viewmodel.TabManager
import com.aandios.nous.core.workspace.viewmodel.WindowManager
import com.aandios.nous_platform.ui.terminalLayout.TerminalLayout
import com.aandios.nous_platform.ui.terminalLayout.TerminalStateViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

    // Публикуем границы окна (px) для hit-test переноса вкладок + персистим геометрию
    LaunchedEffect(session.id, windowState) {
        snapshotFlow { windowState.size to windowState.position }.collectLatest {
            val pos = windowState.position
            val x = if (pos.isSpecified) with(density) { pos.x.roundToPx() } else null
            val y = if (pos.isSpecified) with(density) { pos.y.roundToPx() } else null
            val w = with(density) { windowState.size.width.roundToPx() }
            val h = with(density) { windowState.size.height.roundToPx() }
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

    // Перенос вкладки между окнами
    var dragPayload by remember { mutableStateOf<TabDragBus.Payload?>(null) }
    val externalDrag by dragBus.active.collectAsState()
    val externalDragActive = externalDrag?.let { it.sourceWindowId != session.id } == true

    TerminalLayout(
        modifier = Modifier.fillMaxHeight(),
        terminalState = terminalStateViewModel,
        tabManager = tabManager,
        workspaceRepo = workspaceRepo,
        templateRepo = templateRepo,
        workspaceBus = workspaceBus,
        activeWorkspaceId = session.activeWorkspaceId,
        onOpenWorkspace = { config -> windowManager.openWorkspaceIn(session.id, config) },
        onSymbolSelected = { terminalStateViewModel.changeSymbol(it) },
        onTimeframeSelected = { terminalStateViewModel.changeTimeFrame(it) },
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
            Column(Modifier.fillMaxWidth().weight(1f)) {
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
                        windowManager.detachWorkspace(
                            wsId,
                            session.id,
                            cursor?.first,
                            cursor?.second
                        )
                    },
                    externalDragActive = externalDragActive,
                )
                Box(Modifier.fillMaxSize()) {
                    activeWorkspace?.let { ws ->
                        WorkspaceView(
                            ws = ws,
                            workspaceRepo = workspaceRepo,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
