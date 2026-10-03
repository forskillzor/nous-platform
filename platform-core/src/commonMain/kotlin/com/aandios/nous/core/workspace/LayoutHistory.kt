/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

/**
 * Снимок layout-состояния для undo/redo сплитов.
 */
data class LayoutSnapshot(
    val layout: LayoutNode,
    val panels: Map<String, PanelConfig>,
)

/**
 * История операций над раскладкой workspace (close/split/move/resize).
 * Undo/redo по Ctrl+Z / Ctrl+Shift+Z — только клавиатура, без кнопок.
 */
class LayoutHistory(private val capacity: Int = 100) {
    private val undoStack = ArrayDeque<LayoutSnapshot>()
    private val redoStack = ArrayDeque<LayoutSnapshot>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Записать состояние ДО операции. Очищает redo. */
    fun push(snapshot: LayoutSnapshot) {
        undoStack.addLast(snapshot)
        if (undoStack.size > capacity) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: LayoutSnapshot): LayoutSnapshot? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        return undoStack.removeLast()
    }

    fun redo(current: LayoutSnapshot): LayoutSnapshot? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current)
        return redoStack.removeLast()
    }
}
