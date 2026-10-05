/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.paper

import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.adapters.TradingAdapter

/**
 * Общий экземпляр paper-движка (один демо-счёт на процесс).
 *
 * Сам режим paper — НЕ глобальный: каждая панель (chart/DOM/trading panel)
 * хранит свой флаг и выбирает адаптер через [effectiveTrading] — в одном
 * workspace можно одновременно видеть paper и live данные.
 */
object PaperTrading {
    val adapter: PaperTradingAdapter = PaperTradingAdapter()

    /** Ключ StateStore, под которым UI персистит тумблер демо-торговли. */
    const val STORE_KEY = "paper_enabled"
}

/**
 * Торговый адаптер для конкретной панели: при включённом режиме paper —
 * общий paper-адаптер (работает с ЛЮБЫМ провайдером), иначе — реальный
 * адаптер провайдера.
 */
fun Provider.effectiveTrading(paper: Boolean): TradingAdapter? =
    if (paper) PaperTrading.adapter else trading
