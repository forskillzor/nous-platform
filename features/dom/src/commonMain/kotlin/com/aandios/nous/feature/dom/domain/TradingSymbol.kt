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
 */
data class TradingSymbol(
    val symbol: String,
    val displayName: String,
    val provider: TradingProvider,
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
         * Создаёт TradingSymbol из SymbolInfo (данные symbolInfoAdapter).
         */
        fun fromSymbolInfo(info: SymbolInfo, provider: TradingProvider): TradingSymbol {
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
        /**
         * Стандартные символы для Binance Spot.
         */
        val BINANCE_SPOT_SYMBOLS = listOf(
            TradingSymbol("BTCUSDT", "BTC/USDT", TradingProvider.BINANCE),
            TradingSymbol("ETHUSDT", "ETH/USDT", TradingProvider.BINANCE),
            TradingSymbol("BNBUSDT", "BNB/USDT", TradingProvider.BINANCE),
            TradingSymbol("SOLUSDT", "SOL/USDT", TradingProvider.BINANCE),
            TradingSymbol("XRPUSDT", "XRP/USDT", TradingProvider.BINANCE),
            TradingSymbol("ADAUSDT", "ADA/USDT", TradingProvider.BINANCE),
            TradingSymbol("DOGEUSDT", "DOGE/USDT", TradingProvider.BINANCE),
            TradingSymbol("DOTUSDT", "DOT/USDT", TradingProvider.BINANCE),
            TradingSymbol("AVAXUSDT", "AVAX/USDT", TradingProvider.BINANCE),
            TradingSymbol("LINKUSDT", "LINK/USDT", TradingProvider.BINANCE),
        )

        /**
         * Стандартные символы для Binance Coin-M Futures.
         */
        val BINANCE_COIN_M_FUTURES_SYMBOLS = listOf(
            TradingSymbol("BTCUSD_PERP", "BTCUSD Perp", TradingProvider.BINANCE_COIN_M),
            TradingSymbol("ETHUSD_PERP", "ETHUSD Perp", TradingProvider.BINANCE_COIN_M),
            TradingSymbol("BNBUSD_PERP", "BNBUSD Perp", TradingProvider.BINANCE_COIN_M),
            TradingSymbol("SOLUSD_PERP", "SOLUSD Perp", TradingProvider.BINANCE_COIN_M),
            TradingSymbol("XRPUSD_PERP", "XRPUSD Perp", TradingProvider.BINANCE_COIN_M),
            TradingSymbol("ADAUSD_PERP", "ADAUSD Perp", TradingProvider.BINANCE_COIN_M),
            TradingSymbol("DOGEUSD_PERP", "DOGEUSD Perp", TradingProvider.BINANCE_COIN_M),
        )

        /**
         * Стандартные символы для Bybit Spot.
         */
        val BYBIT_SPOT_SYMBOLS = listOf(
            TradingSymbol("BTCUSDT", "BTC/USDT", TradingProvider.BYBIT),
            TradingSymbol("ETHUSDT", "ETH/USDT", TradingProvider.BYBIT),
            TradingSymbol("XRPUSDT", "XRP/USDT", TradingProvider.BYBIT),
            TradingSymbol("SOLUSDT", "SOL/USDT", TradingProvider.BYBIT),
        )

        /**
         * Получает список символов для указанного провайдера.
         */
        // fixme remove hardcode
        fun getSymbolsForProvider(provider: TradingProvider): List<TradingSymbol> {
            return when (provider) {
                TradingProvider.BINANCE -> BINANCE_SPOT_SYMBOLS
                TradingProvider.BINANCE_COIN_M -> BINANCE_COIN_M_FUTURES_SYMBOLS
                TradingProvider.BINANCE_USDM -> BINANCE_SPOT_SYMBOLS.take(5) // Пример для USD-M
                TradingProvider.BYBIT -> BYBIT_SPOT_SYMBOLS
                TradingProvider.KRAKEN -> BINANCE_SPOT_SYMBOLS.take(5) // Пример для Kraken
            }
        }

        /**
         * Находит символ по строковому идентификатору.
         */
        fun findSymbol(symbolString: String, provider: TradingProvider): TradingSymbol? {
            return getSymbolsForProvider(provider).find { it.symbol == symbolString }
        }

        /**
         * Создает символ по умолчанию для провайдера.
         */
        fun defaultForProvider(provider: TradingProvider): TradingSymbol {
            return getSymbolsForProvider(provider).firstOrNull() ?: 
                TradingSymbol("BTCUSDT", "BTC/USDT", provider)
        }

        /**
         * Возвращает символ по умолчанию (для Binance Coin-M Futures).
         */
        fun default(): TradingSymbol = defaultForProvider(TradingProvider.BINANCE_COIN_M)
    }

    override fun toString(): String = displayName
}