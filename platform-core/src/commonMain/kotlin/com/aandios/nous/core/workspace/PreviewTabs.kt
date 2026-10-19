/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

/**
 * Чистые переходы состояния вкладок окна для preview-режима (как в IDEA):
 * preview ровно одна на окно, открытие другого workspace в preview занимает
 * её слот; обычное открытие закрепляет (промоутит) preview, дубликат вкладки
 * невозможен.
 */
fun WindowSessionState.openPreview(workspaceId: String): WindowSessionState {
    if (workspaceId in workspaceIds) return copy(activeWorkspaceId = workspaceId)
    val preview = previewWorkspaceId?.takeIf { it in workspaceIds }
    return if (preview != null) {
        copy(
            workspaceIds = workspaceIds.map { if (it == preview) workspaceId else it },
            activeWorkspaceId = workspaceId,
            previewWorkspaceId = workspaceId,
        )
    } else {
        copy(
            workspaceIds = workspaceIds + workspaceId,
            activeWorkspaceId = workspaceId,
            previewWorkspaceId = workspaceId,
        )
    }
}

/** Открыть обычной (закреплённой) вкладкой: уже открытый — активируется, preview промоутится. */
fun WindowSessionState.openPinned(workspaceId: String): WindowSessionState {
    val promotedPreview = previewWorkspaceId?.takeIf { it != workspaceId }
    return if (workspaceId in workspaceIds) {
        copy(activeWorkspaceId = workspaceId, previewWorkspaceId = promotedPreview)
    } else {
        copy(
            workspaceIds = workspaceIds + workspaceId,
            activeWorkspaceId = workspaceId,
            previewWorkspaceId = promotedPreview,
        )
    }
}

/** Закрепить preview-вкладку (двойной клик по вкладке в TabBar). */
fun WindowSessionState.promotePreview(workspaceId: String): WindowSessionState =
    if (previewWorkspaceId == workspaceId) {
        copy(activeWorkspaceId = workspaceId, previewWorkspaceId = null)
    } else {
        copy(activeWorkspaceId = workspaceId)
    }

/** Убрать вкладку из окна (закрытие/перенос): preview-флаг не переживает удаление. */
fun WindowSessionState.withoutWorkspace(workspaceId: String): WindowSessionState {
    val ids = workspaceIds - workspaceId
    return copy(
        workspaceIds = ids,
        activeWorkspaceId = activeWorkspaceId?.takeIf { it != workspaceId } ?: ids.lastOrNull(),
        previewWorkspaceId = previewWorkspaceId?.takeIf { it != workspaceId },
    )
}
