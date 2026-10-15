/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.ui

import com.aandios.nous.api.market.paper.PaperTrading
import com.aandios.nous.core.storage.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Глобальный Paper-флаг docked trading panel (`paper_enabled` в StateStore).
 *
 * Один экземпляр на приложение: TopBar и все TradingViewModel читают/пишут
 * его, поэтому свич в верхней полосе и панель всегда синхронны. Персист —
 * тот же ключ, что исторически использовала панель.
 */
class PaperModeState(private val stateStore: StateStore?) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    init {
        scope.launch {
            _enabled.value = stateStore?.getString(PaperTrading.STORE_KEY) == "1"
        }
    }

    fun set(enabled: Boolean) {
        if (_enabled.value == enabled) return
        _enabled.value = enabled
        scope.launch {
            stateStore?.putString(PaperTrading.STORE_KEY, if (enabled) "1" else "0")
        }
    }
}
