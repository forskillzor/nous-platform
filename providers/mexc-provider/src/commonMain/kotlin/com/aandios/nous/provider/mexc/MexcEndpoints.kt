/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

/**
 * REST base MEXC Futures (Contract API).
 * Примечание: testnet MEXC публично не документирован — используется только mainnet.
 */
internal const val MEXC_BASE_URL = "https://contract.mexc.com"

/**
 * Единый формат символа в платформе: UPPERCASE без разделителей ("BTCUSDT").
 * Провайдер отвечает за конвертацию в/из своего нативного формата
 * (у MEXC Futures — "BTC_USDT").
 */

/** Единый формат ("BTCUSDT") → нативный MEXC ("BTC_USDT"). Нативный формат пропускаем. */
fun toMexcSymbol(symbol: String): String {
    val upper = symbol.uppercase()
    if (upper.contains("_")) return upper
    val quotes = listOf("USDT", "USDC", "BTC", "ETH", "BNB", "DAI", "USD")
    val quote = quotes.firstOrNull { upper.endsWith(it) }
    return if (quote != null && upper.length > quote.length) {
        "${upper.dropLast(quote.length)}_$quote"
    } else {
        upper
    }
}

/** Нативный символ MEXC ("BTC_USDT") → единый формат ("BTCUSDT"). */
fun fromMexcSymbol(symbol: String): String = symbol.replace("_", "").uppercase()

/** Веса запросов для [MexcRestGate] (по лимитам MEXC Contract API: 20 req/2s на endpoint). */
internal object MexcWeights {
    const val KLINE = 20
    const val DEPTH = 20
    const val DEALS = 20
    const val TICKER = 20
    const val DETAIL = 1
}
