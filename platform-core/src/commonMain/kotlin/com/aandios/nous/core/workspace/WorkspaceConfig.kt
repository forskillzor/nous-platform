/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlinx.serialization.Serializable

/**
 * Корневой документ — полное описание одного workspace.
 * Аналог файла проекта в IDE.
 */
@Serializable
data class WorkspaceConfig(
    val id: String = generateId(),
    val name: String = "Untitled",
    val group: String = "",
    val icon: String? = null,

    val providers: List<ProviderRef> = emptyList(),
    val layout: LayoutNode = LayoutNode.Leaf("panel-0"),
    val panels: List<PanelConfig> = emptyList(),

    val createdAt: Long = currentTime(),
    val updatedAt: Long = currentTime(),
    val order: Int = 0
)
