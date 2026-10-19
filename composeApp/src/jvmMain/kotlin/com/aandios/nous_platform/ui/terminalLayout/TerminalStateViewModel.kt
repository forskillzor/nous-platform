/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import kotlinx.coroutines.flow.MutableStateFlow

class TerminalStateViewModel {
    val selectedSymbol = MutableStateFlow<String>("BTCUSDT")

    // Биржа выбранного инструмента: один и тот же символ на разных биржах —
    // разные элементы (выделение в Market Watch идёт по паре symbol + exchange).
    val selectedProviderId = MutableStateFlow<String?>(null)
    val selectedTimeFrame = MutableStateFlow("1h")

    fun changeSymbol(symbol: String, providerId: String? = null) {
        selectedSymbol.value = symbol
        selectedProviderId.value = providerId
    }

    fun changeTimeFrame(timeframe: String) {
        selectedTimeFrame.value = timeframe
    }
}