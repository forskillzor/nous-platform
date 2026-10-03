/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace.viewmodel

import com.aandios.nous.core.Disposable
import com.aandios.nous.core.workspace.WorkspaceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Хранилище ViewModel'ов всех открытых workspace'ов (общее для всех окон).
 * Раскладкой вкладок по окнам и активной вкладкой управляет WindowManager.
 */
class TabManager {
    private val _workspaces = MutableStateFlow<List<WorkspaceViewModel>>(emptyList())
    val workspaces: StateFlow<List<WorkspaceViewModel>> = _workspaces

    fun workspaceById(id: String): WorkspaceViewModel? =
        _workspaces.value.find { it.config.id == id }

    suspend fun openWorkspace(
        config: WorkspaceConfig,
        panels: Map<String, PanelViewModel> = emptyMap()
    ): WorkspaceViewModel {
        val existing = workspaceById(config.id)
        if (existing != null) return existing
        val vm = WorkspaceViewModel(config.id, config, panels)
        _workspaces.update { it + vm }
        return vm
    }

    suspend fun closeWorkspace(workspaceId: String) {
        val workspace = workspaceById(workspaceId) ?: return
        workspace.liveViewModels.values.forEach { vm ->
            (vm as? Disposable)?.dispose()
        }
        workspace.liveViewModels.clear()
        _workspaces.update { list -> list.filterNot { it.config.id == workspaceId } }
    }
}
