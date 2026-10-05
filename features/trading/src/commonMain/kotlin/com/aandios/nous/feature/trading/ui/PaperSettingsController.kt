/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Глобальный контроллер окна настроек paper trading (открытие/закрытие).
 * Окно рендерится оверлеем на корне приложения (Dialog в SwingWindow
 * не отображается), поэтому состояние — общий singleton.
 */
class PaperSettingsController {

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun open() {
        _visible.value = true
    }

    fun close() {
        _visible.value = false
    }
}
