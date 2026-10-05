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

/** Веса запросов для [MexcRestGate] (по лимитам MEXC Contract API: 20 req/2s на endpoint). */
internal object MexcWeights {
    const val KLINE = 20
    const val DEPTH = 20
    const val DEALS = 20
    const val TICKER = 20
    const val DETAIL = 1
}
