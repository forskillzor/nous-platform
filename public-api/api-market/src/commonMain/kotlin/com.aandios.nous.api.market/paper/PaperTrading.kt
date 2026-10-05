/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.paper

import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.adapters.TradingAdapter

/**
 * Глобальный тумблер демо-торговли и общий экземпляр [PaperTradingAdapter].
 *
 * Включение/выключение — процессный флаг (для демо/тестов); при включении
 * ВСЕ панели (trading/DOM/chart) используют paper-адаптер вместо реального
 * адаптера активного провайдера — см. [effectiveTrading].
 */
object PaperTrading {
    val adapter: PaperTradingAdapter = PaperTradingAdapter()

    /** Ключ StateStore, под которым UI персистит тумблер демо-торговли. */
    const val STORE_KEY = "paper_enabled"

    @Volatile
    var enabled: Boolean = false
}

/**
 * Торговый адаптер для использования в UI: при включённой демо-торговле —
 * общий paper-адаптер (работает с ЛЮБЫМ провайдером), иначе — реальный
 * адаптер провайдера.
 */
fun Provider.effectiveTrading(): TradingAdapter? =
    if (PaperTrading.enabled) PaperTrading.adapter else trading
