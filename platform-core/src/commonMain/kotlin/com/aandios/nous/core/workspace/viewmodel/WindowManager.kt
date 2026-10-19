/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace.viewmodel

import com.aandios.nous.core.workspace.AppConfig
import com.aandios.nous.core.workspace.AppStateRepository
import com.aandios.nous.core.workspace.WindowSessionState
import com.aandios.nous.core.workspace.WorkspaceRepository
import com.aandios.nous.core.workspace.openPinned
import com.aandios.nous.core.workspace.openPreview
import com.aandios.nous.core.workspace.promotePreview
import com.aandios.nous.core.workspace.withoutWorkspace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Управляет окнами терминала (вкладки как в браузере):
 * у каждого окна свой список workspace-вкладок, активная вкладка и одна
 * preview-вкладка (одиночный клик в навигаторе, курсив в TabBar).
 * ViewModel'ы всех workspace'ов живут в TabManager и переживают
 * переносы между окнами.
 */
class WindowManager(
    private val tabManager: TabManager,
    private val workspaceRepo: WorkspaceRepository,
    private val appStateRepo: AppStateRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _sessions = MutableStateFlow<List<WindowSessionState>>(emptyList())
    val sessions: StateFlow<List<WindowSessionState>> = _sessions

    fun sessionById(id: String): WindowSessionState? =
        _sessions.value.find { it.id == id }

    suspend fun restoreSessions() {
        val cfg = appStateRepo.restore() ?: AppConfig()
        val states = when {
            cfg.windows.isNotEmpty() -> cfg.windows
            cfg.openWorkspaceIds.isNotEmpty() -> listOf(
                WindowSessionState(
                    workspaceIds = cfg.openWorkspaceIds,
                    activeWorkspaceId = cfg.activeWorkspaceId,
                )
            )
            else -> emptyList()
        }

        if (states.isEmpty()) {
            _sessions.value = listOf(WindowSessionState())
            persist()
            return
        }

        val valid = states.mapNotNull { st ->
            val ids = st.workspaceIds.filter { workspaceRepo.get(it) != null }.distinct()
            if (ids.isEmpty()) return@mapNotNull null
            st.copy(
                workspaceIds = ids,
                activeWorkspaceId = st.activeWorkspaceId?.takeIf { it in ids } ?: ids.lastOrNull(),
                previewWorkspaceId = st.previewWorkspaceId?.takeIf { it in ids },
            )
        }

        val result = if (valid.isEmpty()) listOf(WindowSessionState()) else valid
        for (st in result) {
            for (id in st.workspaceIds) {
                workspaceRepo.get(id)?.let { tabManager.openWorkspace(it) }
            }
        }
        _sessions.value = result
        persist()
    }

    /** Открыть workspace закреплённой вкладкой (двойной клик, шаблоны). Дубликат невозможен. */
    fun openWorkspaceIn(sessionId: String, config: com.aandios.nous.core.workspace.WorkspaceConfig) {
        scope.launch { tabManager.openWorkspace(config) }
        updateSession(sessionId) { it.openPinned(config.id) }
    }

    /** Открыть workspace в preview-вкладке (одиночный клик); уже открытый — переключение. */
    fun openWorkspacePreviewIn(sessionId: String, config: com.aandios.nous.core.workspace.WorkspaceConfig) {
        scope.launch { tabManager.openWorkspace(config) }
        val replacedPreview = sessionById(sessionId)?.let { st ->
            if (config.id in st.workspaceIds) null
            else st.previewWorkspaceId?.takeIf { it in st.workspaceIds }
        }
        updateSession(sessionId) { it.openPreview(config.id) }
        replacedPreview?.let { disposeIfUnused(it) }
    }

    /** Закрепить preview-вкладку (двойной клик по вкладке в TabBar). */
    fun promotePreview(sessionId: String, workspaceId: String) {
        updateSession(sessionId) { it.promotePreview(workspaceId) }
    }

    /** Открыть существующий workspace во вкладке (без дублирования вкладки). */
    fun openWorkspaceInSession(sessionId: String, workspaceId: String) {
        scope.launch {
            workspaceRepo.get(workspaceId)?.let { tabManager.openWorkspace(it) }
        }
        updateSession(sessionId) { it.openPinned(workspaceId) }
    }

    fun activate(sessionId: String, workspaceId: String) {
        updateSession(sessionId) { it.copy(activeWorkspaceId = workspaceId) }
    }

    fun closeWorkspaceIn(sessionId: String, workspaceId: String) {
        updateSession(sessionId) { it.withoutWorkspace(workspaceId) }
        disposeIfUnused(workspaceId)
    }

    fun reorderWorkspace(sessionId: String, fromIndex: Int, toIndex: Int) {
        updateSession(sessionId) { st ->
            val ids = st.workspaceIds.toMutableList()
            if (fromIndex in ids.indices && toIndex in ids.indices && fromIndex != toIndex) {
                val item = ids.removeAt(fromIndex)
                ids.add(toIndex, item)
            }
            st.copy(workspaceIds = ids)
        }
    }

    /** Вынести вкладку в новое окно (detach) с позицией курсора. Preview промоутится. */
    fun detachWorkspace(workspaceId: String, fromSessionId: String, x: Int? = null, y: Int? = null) {
        updateSession(fromSessionId) { it.withoutWorkspace(workspaceId) }
        val newSession = WindowSessionState(
            workspaceIds = listOf(workspaceId),
            activeWorkspaceId = workspaceId,
            x = x,
            y = y,
        )
        _sessions.update { it + newSession }
        persist()
    }

    /** Перенести вкладку между окнами (перетаскивание между окнами). Preview промоутится. */
    fun moveWorkspace(workspaceId: String, fromSessionId: String, toSessionId: String, toIndex: Int) {
        if (fromSessionId == toSessionId) return
        val to = sessionById(toSessionId) ?: return
        updateSession(fromSessionId) { it.withoutWorkspace(workspaceId) }
        updateSession(toSessionId) { st ->
            val ids = st.workspaceIds.toMutableList()
            ids.add(toIndex.coerceIn(0, ids.size), workspaceId)
            st.copy(workspaceIds = ids.distinct(), activeWorkspaceId = workspaceId)
        }
    }

    /** Создать новое пустое окно (Welcome). */
    fun newEmptySession(x: Int? = null, y: Int? = null) {
        _sessions.update { it + WindowSessionState(x = x, y = y) }
        persist()
    }

    fun updateSessionGeometry(sessionId: String, x: Int?, y: Int?, width: Int, height: Int) {
        updateSession(sessionId) { it.copy(x = x, y = y, width = width, height = height) }
    }

    /**
     * Закрыть окно. Workspace'ы, которых больше нет ни в одном окне, закрываются
     * (dispose VM), но остаются в WorkspaceRepository (можно открыть заново).
     * @return false, если это было последнее окно (приложению пора выйти).
     */
    fun closeSession(sessionId: String): Boolean {
        val st = sessionById(sessionId) ?: return _sessions.value.isNotEmpty()
        val remaining = _sessions.value.filterNot { it.id == sessionId }
        _sessions.value = remaining
        for (id in st.workspaceIds) {
            if (remaining.none { id in it.workspaceIds }) {
                scope.launch { tabManager.closeWorkspace(id) }
            }
        }
        persist()
        return remaining.isNotEmpty()
    }

    private fun disposeIfUnused(workspaceId: String) {
        if (_sessions.value.none { workspaceId in it.workspaceIds }) {
            scope.launch { tabManager.closeWorkspace(workspaceId) }
        }
    }

    private fun updateSession(sessionId: String, transform: (WindowSessionState) -> WindowSessionState) {
        val st = sessionById(sessionId) ?: return
        _sessions.update { list -> list.map { if (it.id == sessionId) transform(it) else it } }
        persist()
    }

    private fun persist() {
        val cfg = AppConfig(windows = _sessions.value)
        scope.launch { appStateRepo.save(cfg) }
    }
}
