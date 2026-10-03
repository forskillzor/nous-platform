/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace.viewmodel

import com.aandios.nous.core.workspace.AppConfig
import com.aandios.nous.core.workspace.AppStateRepository
import com.aandios.nous.core.workspace.WindowSessionState
import com.aandios.nous.core.workspace.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Управляет окнами терминала (вкладки как в браузере):
 * у каждого окна свой список workspace-вкладок и активная вкладка.
 * ViewModel'ы всех workspace'ов живут в TabManager и переживают
 * переносы между окнами.
 */
class WindowManager(
    private val tabManager: TabManager,
    private val workspaceRepo: WorkspaceRepository,
    private val appStateRepo: AppStateRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
            val ids = st.workspaceIds.filter { workspaceRepo.get(it) != null }
            if (ids.isEmpty()) return@mapNotNull null
            st.copy(
                workspaceIds = ids,
                activeWorkspaceId = st.activeWorkspaceId?.takeIf { it in ids }
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

    /** Открыть workspace во вкладке указанного окна (создать новую вкладку). */
    fun openWorkspaceIn(sessionId: String, config: com.aandios.nous.core.workspace.WorkspaceConfig) {
        scope.launch { tabManager.openWorkspace(config) }
        updateSession(sessionId) { st ->
            st.copy(
                workspaceIds = st.workspaceIds + config.id,
                activeWorkspaceId = config.id,
            )
        }
    }

    /** Открыть существующий workspace во вкладке (без дублирования вкладки). */
    fun openWorkspaceInSession(sessionId: String, workspaceId: String) {
        scope.launch {
            workspaceRepo.get(workspaceId)?.let { tabManager.openWorkspace(it) }
        }
        updateSession(sessionId) { st ->
            if (workspaceId in st.workspaceIds) {
                st.copy(activeWorkspaceId = workspaceId)
            } else {
                st.copy(
                    workspaceIds = st.workspaceIds + workspaceId,
                    activeWorkspaceId = workspaceId,
                )
            }
        }
    }

    fun activate(sessionId: String, workspaceId: String) {
        updateSession(sessionId) { it.copy(activeWorkspaceId = workspaceId) }
    }

    fun closeWorkspaceIn(sessionId: String, workspaceId: String) {
        updateSession(sessionId) { st ->
            val ids = st.workspaceIds - workspaceId
            st.copy(
                workspaceIds = ids,
                activeWorkspaceId = st.activeWorkspaceId?.takeIf { it != workspaceId }
                    ?: ids.lastOrNull(),
            )
        }
        if (_sessions.value.none { workspaceId in it.workspaceIds }) {
            scope.launch { tabManager.closeWorkspace(workspaceId) }
        }
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

    /** Вынести вкладку в новое окно (detach) с позицией курсора. */
    fun detachWorkspace(workspaceId: String, fromSessionId: String, x: Int? = null, y: Int? = null) {
        updateSession(fromSessionId) { st ->
            val ids = st.workspaceIds - workspaceId
            st.copy(
                workspaceIds = ids,
                activeWorkspaceId = st.activeWorkspaceId?.takeIf { it != workspaceId }
                    ?: ids.lastOrNull(),
            )
        }
        val newSession = WindowSessionState(
            workspaceIds = listOf(workspaceId),
            activeWorkspaceId = workspaceId,
            x = x,
            y = y,
        )
        _sessions.update { it + newSession }
        persist()
    }

    /** Перенести вкладку между окнами (перетаскивание между окнами). */
    fun moveWorkspace(workspaceId: String, fromSessionId: String, toSessionId: String, toIndex: Int) {
        if (fromSessionId == toSessionId) return
        val to = sessionById(toSessionId) ?: return
        updateSession(fromSessionId) { st ->
            val ids = st.workspaceIds - workspaceId
            st.copy(
                workspaceIds = ids,
                activeWorkspaceId = st.activeWorkspaceId?.takeIf { it != workspaceId }
                    ?: ids.lastOrNull(),
            )
        }
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
