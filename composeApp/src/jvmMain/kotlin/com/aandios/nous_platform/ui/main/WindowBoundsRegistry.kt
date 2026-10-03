/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.main

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Реестр границ окон терминала в экранных координатах (px).
 * Используется для hit-test при перетаскивании вкладок между окнами:
 * окно-источник по глобальной позиции курсора определяет целевое окно.
 */
object WindowBoundsRegistry {
    data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(px: Int, py: Int): Boolean =
            px in x..(x + width) && py in y..(y + height)
    }

    private val _bounds = MutableStateFlow<Map<String, Bounds>>(emptyMap())
    val bounds: StateFlow<Map<String, Bounds>> = _bounds

    fun update(windowId: String, b: Bounds) {
        _bounds.update { it + (windowId to b) }
    }

    fun remove(windowId: String) {
        _bounds.update { it - windowId }
    }

    fun boundsAt(px: Int, py: Int): String? =
        _bounds.value.entries.firstOrNull { it.value.contains(px, py) }?.key

    fun cursorLocation(): Pair<Int, Int>? =
        try {
            java.awt.MouseInfo.getPointerInfo()?.location?.let { it.x to it.y }
        } catch (_: Throwable) {
            null
        }
}
