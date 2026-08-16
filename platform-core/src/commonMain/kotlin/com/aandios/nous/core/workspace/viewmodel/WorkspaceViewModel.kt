/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace.viewmodel

import com.aandios.nous.core.workspace.WorkspaceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WorkspaceViewModel(
    val workspaceId: String,
    var config: WorkspaceConfig,
    val panels: Map<String, PanelViewModel>
) {
    private val _isActive = MutableStateFlow(true)
    val isActive: StateFlow<Boolean> = _isActive

    fun updateConfig(newConfig: WorkspaceConfig) { config = newConfig }

    /** Live panel ViewModels survive tab switches — keyed by panelId */
    val liveViewModels = mutableMapOf<String, Any>()

    /** Incremented on each activation — used to force recompose effects */
    var activationCount = 0
}
