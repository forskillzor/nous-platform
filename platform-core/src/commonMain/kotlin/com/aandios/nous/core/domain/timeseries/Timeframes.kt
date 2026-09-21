/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.domain.timeseries

/**
 * Единый маппинг таймфреймов: строка UI → интервал биржи и длительность в мс.
 * Устраняет дублирование маппинга в репозитории, ViewModel и рендерерах.
 */
object Timeframes {

    val supported: Set<String> = setOf("1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w")

    /** Интервал, который понимает биржа. Неизвестные значения — часовой. */
    fun toExchangeInterval(timeframe: String): String =
        if (timeframe in supported) timeframe else "1h"

    /** Длительность таймфрейма в миллисекундах. */
    fun millis(timeframe: String): Long = when (timeframe) {
        "1m" -> 60_000L
        "5m" -> 300_000L
        "15m" -> 900_000L
        "30m" -> 1_800_000L
        "1h" -> 3_600_000L
        "4h" -> 14_400_000L
        "1d" -> 86_400_000L
        "1w" -> 604_800_000L
        else -> 3_600_000L
    }
}
