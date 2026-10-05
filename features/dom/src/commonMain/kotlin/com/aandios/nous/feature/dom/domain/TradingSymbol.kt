/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain

import com.aandios.nous.api.market.model.SymbolInfo
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/**
 * Торговый символ (пара) для отображения.
 * Содержит SymbolInfo с tickSize/minQty для форматирования цен и объёмов.
 *
 * [provider] — id провайдера из ProviderRegistry, от которого пришёл символ.
 */
data class TradingSymbol(
    val symbol: String,
    val displayName: String,
    val provider: String,
    val symbolInfo: SymbolInfo? = null
) {
    private fun formatNumber(value: Double, decimals: Int): String {
        val factor = 10.0.pow(decimals)
        val rounded = round(value * factor) / factor
        val parts = rounded.toString().split(".")
        val intPart = parts[0]
        val decPart = if (parts.size > 1) parts[1] else ""
        val paddedDec = decPart.padEnd(decimals, '0').take(decimals)
        return if (decimals > 0) "$intPart.$paddedDec" else intPart
    }

    /** Форматирует цену с учётом tickSize инструмента */
    fun formatPrice(price: Double): String {
        val tickSize = symbolInfo?.tickSize ?: 0.01
        val decimals = if (tickSize <= 0.0) 2 else maxOf(0, -log10(tickSize).toInt())
        val d = when {
            price >= 10_000 -> maxOf(decimals - 1, 0)
            price >= 1 -> decimals
            else -> decimals + 1
        }
        return formatNumber(price, d)
    }

    fun formatPrice(price: Float): String = formatPrice(price.toDouble())

    /** Форматирует объём с суффиксами K/M */
    fun formatVolume(volume: Double): String {
        val minQty = symbolInfo?.minQty ?: 0.001
        val decimals = if (minQty <= 0.0) 3 else maxOf(0, -log10(minQty).toInt())
        val v = abs(volume)
        return when {
            v >= 1_000_000 -> "${formatNumber(volume / 1_000_000, maxOf(1, decimals - 2))}M"
            v >= 1_000 -> "${formatNumber(volume / 1_000, maxOf(1, decimals - 1))}K"
            v >= 100 -> formatNumber(volume, 0)
            v >= 10 -> formatNumber(volume, 1)
            v >= 1 -> formatNumber(volume, decimals.coerceAtMost(2))
            else -> formatNumber(volume, decimals)
        }
    }

    companion object {

        /**
         * Создаёт TradingSymbol из SymbolInfo (данные symbolInfoAdapter провайдера).
         */
        fun fromSymbolInfo(info: SymbolInfo, provider: String): TradingSymbol {
            val displayName = if (info.baseAsset.isNotEmpty() && info.quoteAsset.isNotEmpty()) {
                "${info.baseAsset}/${info.quoteAsset}"
            } else {
                info.symbol
            }
            return TradingSymbol(
                symbol = info.symbol,
                displayName = displayName,
                provider = provider,
                symbolInfo = info
            )
        }

        /** Символ-заглушка для провайдера (пока не пришёл реальный список). */
        fun fallback(symbol: String, provider: String): TradingSymbol =
            TradingSymbol(symbol, symbol, provider)
    }

    override fun toString(): String = displayName
}
