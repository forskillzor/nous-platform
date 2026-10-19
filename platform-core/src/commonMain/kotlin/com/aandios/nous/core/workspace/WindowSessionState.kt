/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlinx.serialization.Serializable

/**
 * Состояние одного окна терминала: набор вкладок (workspace id),
 * активная вкладка, preview-вкладка (одна на окно, как в IDEA) и геометрия.
 * Персистится в AppConfig.windows.
 */
@Serializable
data class WindowSessionState(
    val id: String = generateId(),
    val workspaceIds: List<String> = emptyList(),
    val activeWorkspaceId: String? = null,
    /** Preview-вкладка (курсив в TabBar); null — превью нет. Всегда из [workspaceIds]. */
    val previewWorkspaceId: String? = null,
    /** Позиция окна на экране (px); null — позиция по умолчанию. */
    val x: Int? = null,
    val y: Int? = null,
    val width: Int = 1200,
    val height: Int = 800,
)
