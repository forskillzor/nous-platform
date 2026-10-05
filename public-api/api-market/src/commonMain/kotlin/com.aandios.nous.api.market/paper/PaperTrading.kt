/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.paper

import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.adapters.TradingAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Глобальный тумблер демо-торговли и общий экземпляр [PaperTradingAdapter].
 *
 * Реактивный ([enabledFlow]): все свичи (chart-тулбар, Trading panel) и
 * вью-модели синхронизируются мгновенно; при включении ВСЕ панели
 * (trading/DOM/chart) используют paper-адаптер вместо реального адаптера
 * активного провайдера — см. [effectiveTrading].
 */
object PaperTrading {
    val adapter: PaperTradingAdapter = PaperTradingAdapter()

    /** Ключ StateStore, под которым UI персистит тумблер демо-торговли. */
    const val STORE_KEY = "paper_enabled"

    private val _enabled = MutableStateFlow(false)
    val enabledFlow: StateFlow<Boolean> = _enabled.asStateFlow()

    var enabled: Boolean
        get() = _enabled.value
        set(value) {
            _enabled.value = value
        }
}

/**
 * Торговый адаптер для использования в UI: при включённой демо-торговле —
 * общий paper-адаптер (работает с ЛЮБЫМ провайдером), иначе — реальный
 * адаптер провайдера.
 */
fun Provider.effectiveTrading(): TradingAdapter? =
    if (PaperTrading.enabled) PaperTrading.adapter else trading
