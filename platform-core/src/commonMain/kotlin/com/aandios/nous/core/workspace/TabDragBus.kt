/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Шина перетаскивания вкладки между окнами.
 * Пока активен перенос — все окна видят payload и подсвечивают таб-бары;
 * release отслеживается на уровне AWT, чтобы drag не терялся за границей окна.
 */
class TabDragBus {
    data class Payload(
        val sourceWindowId: String,
        val workspaceId: String,
    )

    private val _active = MutableStateFlow<Payload?>(null)
    val active: StateFlow<Payload?> = _active
    private val _cancelled = MutableStateFlow<Payload?>(null)

    fun start(payload: Payload) {
        _cancelled.value = null
        _active.value = payload
    }

    fun end() {
        _cancelled.value = null
        _active.value = null
    }

    fun end(payload: Payload): Boolean {
        if (_active.value != payload) return false
        _cancelled.value = null
        _active.value = null
        return true
    }

    fun markCancelled(payload: Payload) {
        if (_active.value == payload) _cancelled.value = payload
    }

    fun wasCancelled(payload: Payload): Boolean = _cancelled.value == payload
}
