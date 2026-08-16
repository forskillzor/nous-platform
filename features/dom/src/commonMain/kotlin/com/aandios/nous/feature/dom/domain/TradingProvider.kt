/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain

/**
 * Провайдеры торговых данных для DOM.
 * Поддерживаются различные биржи и источники данных.
 */
enum class TradingProvider(
    val displayName: String,
    val supportsFutures: Boolean = true
) {
    BINANCE("Binance", true),
    BINANCE_COIN_M("Binance Coin-M Futures", true),
    BINANCE_USDM("Binance USD-M Futures", true),
    BYBIT("Bybit", true),
    KRAKEN("Kraken", false);

    companion object {
        /**
         * Возвращает значение по умолчанию (Binance Coin-M Futures).
         */
        fun default(): TradingProvider = BINANCE_COIN_M

        /**
         * Возвращает список всех значений для использования в UI.
         */
        fun all(): List<TradingProvider> = values().toList()

        /**
         * Возвращает список провайдеров, поддерживающих фьючерсы.
         */
        fun futuresProviders(): List<TradingProvider> = values().filter { it.supportsFutures }
    }

    override fun toString(): String = displayName
}