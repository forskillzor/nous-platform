/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * REST-снапшот книги Binance USDT-M (`GET /fapi/v1/depth`).
 * [lastUpdateId] — база для последовательности diff-событий.
 */
@Serializable
data class BinanceDepthSnapshot(
    @SerialName("lastUpdateId") val lastUpdateId: Long = 0,
    @SerialName("bids") val bids: List<List<String>> = emptyList(),
    @SerialName("asks") val asks: List<List<String>> = emptyList(),
)

/**
 * Diff-событие книги Binance USDT-M (`<symbol>@depth@100ms`).
 *
 * [firstUpdateId]/[finalUpdateId] — диапазон id обновления (`U`/`u`),
 * [previousFinalUpdateId] (`pu`) — `u` предыдущего события: обязано совпасть
 * с последним применённым id, иначе книга требует ресинка.
 */
@Serializable
data class BinanceDepthUpdate(
    @SerialName("e") val eventType: String? = null,
    @SerialName("E") val eventTime: Long? = null,
    @SerialName("s") val symbol: String? = null,
    @SerialName("U") val firstUpdateId: Long = 0,
    @SerialName("u") val finalUpdateId: Long = 0,
    @SerialName("pu") val previousFinalUpdateId: Long = 0,
    @SerialName("b") val bids: List<List<String>> = emptyList(),
    @SerialName("a") val asks: List<List<String>> = emptyList(),
)
