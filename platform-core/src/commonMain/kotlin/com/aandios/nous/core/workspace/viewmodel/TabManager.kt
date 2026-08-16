/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace.viewmodel

import com.aandios.nous.core.workspace.AppConfig
import com.aandios.nous.core.workspace.AppStateRepository
import com.aandios.nous.core.workspace.WorkspaceConfig
import com.aandios.nous.core.workspace.WorkspaceRepository
import com.aandios.nous.core.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TabManager(
    private val workspaceRepo: WorkspaceRepository,
    private val appStateRepo: AppStateRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _workspaces = MutableStateFlow<List<WorkspaceViewModel>>(emptyList())
    val workspaces: StateFlow<List<WorkspaceViewModel>> = _workspaces

    private val _activeIndex = MutableStateFlow(0)
    val activeIndex: StateFlow<Int> = _activeIndex

    val activeWorkspace: WorkspaceViewModel?
        get() = _workspaces.value.getOrNull(_activeIndex.value)

    suspend fun openWorkspace(
        config: WorkspaceConfig,
        panels: Map<String, PanelViewModel> = emptyMap()
    ): WorkspaceViewModel {
        val existing = _workspaces.value.find { it.config.id == config.id }
        if (existing != null) {
            _activeIndex.value = _workspaces.value.indexOf(existing)
            return existing
        }
        val vm = WorkspaceViewModel(config.id, config, panels)
        _workspaces.update { it + vm }
        _activeIndex.value = _workspaces.value.lastIndex
        persistState()
        return vm
    }

    suspend fun closeWorkspace(workspaceId: String) {
        val idx = _workspaces.value.indexOfFirst { it.config.id == workspaceId }
        if (idx < 0) return
        val workspace = _workspaces.value[idx]
        // Dispose all live ViewModels
        workspace.liveViewModels.values.forEach { vm ->
            (vm as? Disposable)?.dispose()
        }
        workspace.liveViewModels.clear()
        _workspaces.update { it.toMutableList().also { list -> list.removeAt(idx) } }
        _activeIndex.update { minOf(it, (_workspaces.value.lastIndex).coerceAtLeast(0)) }
        persistState()
    }

    fun reorderWorkspace(fromIndex: Int, toIndex: Int) {
        val list = _workspaces.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _workspaces.value = list
            _activeIndex.update { active ->
                when {
                    active == fromIndex -> toIndex
                    active in (toIndex + 1)..fromIndex -> active + 1
                    active in fromIndex..<toIndex -> active - 1
                    else -> active
                }
            }
            scope.launch { persistState() }
        }
    }

    fun setActive(index: Int) {
        if (index in _workspaces.value.indices) {
            _activeIndex.value = index
            _workspaces.value[index].activationCount++
            scope.launch { persistState() }
        }
    }

    suspend fun restoreSession() {
        val config = appStateRepo.restore() ?: return
        for (id in config.openWorkspaceIds) {
            val ws = workspaceRepo.get(id) ?: continue
            openWorkspace(ws)
        }
        val activeId = config.activeWorkspaceId
        if (activeId != null) {
            val idx = _workspaces.value.indexOfFirst { it.config.id == activeId }
            if (idx >= 0) _activeIndex.value = idx
        }
    }

    private suspend fun persistState() {
        appStateRepo.save(AppConfig(
            openWorkspaceIds = _workspaces.value.map { it.config.id },
            activeWorkspaceId = _workspaces.value.getOrNull(_activeIndex.value)?.config?.id
        ))
    }
}
