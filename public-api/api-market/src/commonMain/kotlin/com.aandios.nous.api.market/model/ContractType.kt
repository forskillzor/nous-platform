/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model

/**
 * Тип фьючерсного контракта:
 *  * [USDT_M] — линейный (маржа в стейбле, qty в базовом активе);
 *  * [COIN_M] — inverse (маржа в монете, qty в контрактах, номинал в USD).
 *
 * Панели фильтруют списки символов по выбранному типу и по-разному считают
 * PnL/маржу (см. [SymbolInfo.isInverse], [SymbolInfo.contractSize]).
 */
enum class ContractType(val label: String) {
    USDT_M("USDT-M"),
    COIN_M("COIN-M");

    /** Подходит ли инструмент этому типу контракта. */
    fun matches(info: SymbolInfo): Boolean = when (this) {
        USDT_M -> !info.isInverse
        COIN_M -> info.isInverse
    }

    companion object {
        fun of(info: SymbolInfo): ContractType = if (info.isInverse) COIN_M else USDT_M
    }
}
