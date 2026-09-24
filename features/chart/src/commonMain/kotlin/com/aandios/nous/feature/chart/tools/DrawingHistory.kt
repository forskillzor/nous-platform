/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.tools

import androidx.compose.runtime.mutableStateListOf

/**
 * Undo/Redo для рисунков на основе **снимков списка до операции**:
 * undo/redo одинаково работают для добавления, удаления и
 * перемещения/изменения фигур.
 *
 * Список рисунков — Compose-состояние, поэтому любые изменения сразу
 * инвалидируют Canvas и snapshotFlow-персистент.
 *
 * Паттерн для drag-жестов: серия [update] (запоминается снимок до первого
 * изменения) + [commit] на отпускании мыши — один шаг undo на весь drag.
 */
class DrawingHistory(private val maxHistory: Int = 100) {
    private val undoStack = ArrayDeque<List<Drawing>>(maxHistory)
    private val redoStack = ArrayDeque<List<Drawing>>(maxHistory)
    private val _drawings = mutableStateListOf<Drawing>()
    val drawings: List<Drawing> get() = _drawings

    // Baseline для drag-серии update: снимок до первого изменения
    private var pendingUndo: List<Drawing>? = null

    /** Добавить рисунок (с записью в undo). */
    fun add(drawing: Drawing) {
        pushUndo()
        _drawings.add(drawing)
    }

    /** Обновить рисунок по id (drag-move/resize). Undo-запись создаётся в [commit]. */
    fun update(id: String, drawing: Drawing) {
        val index = _drawings.indexOfFirst { it.id == id }
        if (index >= 0) {
            if (pendingUndo == null) pendingUndo = _drawings.toList()
            _drawings[index] = drawing
        }
    }

    /** Зафиксировать снимок после серии [update] (мышь отпущена). */
    fun commit() {
        val baseline = pendingUndo ?: return
        pendingUndo = null
        undoStack.addLast(baseline)
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
    }

    fun remove(drawing: Drawing) {
        if (_drawings.contains(drawing)) {
            pushUndo()
            _drawings.remove(drawing)
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_drawings.toList())
        applySnapshot(undoStack.removeLast())
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_drawings.toList())
        applySnapshot(redoStack.removeLast())
    }

    /** Заменяет содержимое (загрузка из персистента). */
    fun replaceAll(drawings: List<Drawing>) {
        _drawings.clear()
        _drawings.addAll(drawings)
        undoStack.clear()
        redoStack.clear()
        pendingUndo = null
    }

    fun clear() {
        _drawings.clear()
        undoStack.clear()
        redoStack.clear()
        pendingUndo = null
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val size: Int get() = _drawings.size

    /** Снимок ДО операции. */
    private fun pushUndo() {
        pendingUndo = null
        undoStack.addLast(_drawings.toList())
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun applySnapshot(snapshot: List<Drawing>) {
        _drawings.clear()
        _drawings.addAll(snapshot)
    }
}
