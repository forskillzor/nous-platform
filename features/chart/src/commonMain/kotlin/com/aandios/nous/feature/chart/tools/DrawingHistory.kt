/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.tools

/**
 * Undo/Redo stack for chart drawings.
 * Single-threaded (UI thread via Compose gestures).
 * Supports Ctrl+Z (undo) and Ctrl+Y (redo).
 */
class DrawingHistory(private val maxHistory: Int = 100) {
    private val undoStack = ArrayDeque<Drawing>(maxHistory)
    private val redoStack = ArrayDeque<Drawing>(maxHistory)
    private val _drawings = mutableListOf<Drawing>()
    val drawings: List<Drawing> get() = _drawings

    fun add(drawing: Drawing) {
        _drawings.add(drawing)
        undoStack.addLast(drawing)
        redoStack.clear()
        if (undoStack.size > maxHistory) undoStack.removeFirst()
    }

    fun undo(): Drawing? {
        val drawing = undoStack.removeLastOrNull() ?: return null
        _drawings.remove(drawing)
        redoStack.addLast(drawing)
        if (redoStack.size > maxHistory) redoStack.removeFirst()
        return drawing
    }

    fun redo(): Drawing? {
        val drawing = redoStack.removeLastOrNull() ?: return null
        _drawings.add(drawing)
        undoStack.addLast(drawing)
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        return drawing
    }

    fun remove(drawing: Drawing) {
        if (_drawings.remove(drawing)) {
            undoStack.remove(drawing)
            redoStack.remove(drawing)
        }
    }

    fun clear() {
        _drawings.clear()
        undoStack.clear()
        redoStack.clear()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val size: Int get() = _drawings.size
}
