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
 * завершение происходит в окне-источнике (release ловится его жестом).
 */
class TabDragBus {
    data class Payload(
        val sourceWindowId: String,
        val workspaceId: String,
    )

    private val _active = MutableStateFlow<Payload?>(null)
    val active: StateFlow<Payload?> = _active

    fun start(payload: Payload) {
        _active.value = payload
    }

    fun end() {
        _active.value = null
    }
}
