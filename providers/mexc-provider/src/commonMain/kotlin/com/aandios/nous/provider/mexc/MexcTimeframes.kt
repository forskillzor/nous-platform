/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

/**
 * Маппинг интервалов приложения → интервалы MEXC Futures (contract API).
 *
 * MEXC использует Pascal-нотацию: Min1, Min5, Min15, Min30, Min60, Hour4,
 * Hour8, Day1, Week1, Month1. В приложении интервалы — стандартные строки
 * Binance-стиля ("1m", "5m", "1h", "4h", "1d", "1w").
 */
object MexcTimeframes {

    /** Интервал приложения → интервал MEXC Futures. */
    fun toMexc(interval: String): String = when (interval) {
        "1m" -> "Min1"
        "5m" -> "Min5"
        "15m" -> "Min15"
        "30m" -> "Min30"
        "1h" -> "Min60"
        "4h" -> "Hour4"
        "1d" -> "Day1"
        "1w" -> "Week1"
        else -> "Min1"
    }

    /** Длительность MEXC-интервала в секундах (для конвертации time-меток). */
    fun intervalSeconds(mexcInterval: String): Long = when (mexcInterval) {
        "Min1" -> 60L
        "Min5" -> 300L
        "Min15" -> 900L
        "Min30" -> 1_800L
        "Min60" -> 3_600L
        "Hour4" -> 14_400L
        "Hour8" -> 28_800L
        "Day1" -> 86_400L
        "Week1" -> 604_800L
        "Month1" -> 2_592_000L
        else -> 60L
    }

    /**
     * Время в миллисекундах: MEXC отдаёт time-метки в СЕКУНДАХ (REST kline
     * `time`, WS `push.kline` `t`) — конвертируем защищённо (если пришло
     * уже в мс — оставляем).
     */
    fun timeToMillis(value: Long): Long =
        if (value in 1..10_000_000_000L) value * 1000 else value
}
