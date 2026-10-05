/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.format

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

/**
 * Унифицированное форматирование цен и объёмов с учётом tickSize и minQty инструмента.
 *
 * @param tickSize минимальный шаг цены (из SymbolInfo, например BTCUSDT=0.01, ETHUSDT=0.1)
 * @param minQty минимальный объём сделки (из SymbolInfo, например BTCUSDT=0.001)
 */
class SymbolFormatter(
    val tickSize: Double = 0.01,
    val minQty: Double = 0.001
) {
    /** Количество знаков после запятой для цен: 0.01→2, 0.1→1, 1.0→0 */
    val priceDecimals: Int = if (tickSize <= 0.0) 2 else maxOf(0, -log10(tickSize).toInt())

    /** Количество знаков после запятой для объёмов: 0.001→3, 0.00001→5 */
    val volumeDecimals: Int = if (minQty <= 0.0) 3 else maxOf(0, max(0, -log10(minQty).toInt()))

    private fun formatNumber(value: Double, decimals: Int): String {
        val factor = 10.0.pow(decimals)
        val rounded = kotlin.math.round(value * factor) / factor
        val parts = rounded.toString().split(".")
        val intPart = parts[0]
        val decPart = if (parts.size > 1) parts[1] else ""
        val paddedDec = decPart.padEnd(decimals, '0').take(decimals)
        return if (decimals > 0) "$intPart.$paddedDec" else intPart
    }

    fun formatPrice(price: Double): String {
        return formatNumber(price, priceDecimals)
    }

    fun formatPrice(price: Float): String = formatPrice(price.toDouble())

    /**
     * Округление цены до tickSize инструмента (шаг цены биржи):
     * 120.706764211 → 120.71 при tickSize=0.01.
     */
    fun roundPrice(price: Double): Double {
        if (tickSize <= 0.0) return price
        return formatPrice(price).toDouble()
    }

    fun formatVolume(volume: Double): String {
        val v = abs(volume)
        return when {
            v >= 1_000_000 -> "${formatNumber(volume / 1_000_000, maxOf(1, volumeDecimals - 2))}M"
            v >= 1_000 -> "${formatNumber(volume / 1_000, maxOf(1, volumeDecimals - 1))}K"
            v >= 100 -> formatNumber(volume, coerceMaxDecimals(0))
            v >= 10 -> formatNumber(volume, coerceMaxDecimals(1))
            v >= 1 -> formatNumber(volume, coerceMaxDecimals(volumeDecimals - 1))
            else -> formatNumber(volume, volumeDecimals)
        }
    }

    fun formatVolume(volume: Float): String = formatVolume(volume.toDouble())

    /**
     * Полное значение объёма без сокращений (K/M) и без потери знаков:
     * разделители тысяч и значащие десятичные, например 1,234 или 12,345.6.
     */
    fun formatVolumeFull(volume: Double): String {
        val plain = formatNumber(abs(volume), volumeDecimals)
        val parts = plain.split(".")
        val intPart = parts[0]
        val decPart = if (parts.size > 1) parts[1] else ""
        val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
        val trimmedDec = decPart.trimEnd('0')
        val sign = if (volume < 0) "-" else ""
        return if (trimmedDec.isEmpty()) "$sign$grouped" else "$sign$grouped.$trimmedDec"
    }

    private fun coerceMaxDecimals(d: Int) = d.coerceAtMost(volumeDecimals).coerceAtLeast(0)

    companion object {
        /** Default formatter for BTCUSDT (tickSize=0.01, minQty=0.001) */
        val DEFAULT = SymbolFormatter()
    }
}
