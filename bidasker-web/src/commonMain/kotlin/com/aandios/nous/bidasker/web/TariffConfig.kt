/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.bidasker.web

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TariffConfig(
    val id: String = "free",
    val name: String = "Free",
    val limits: TariffLimits = TariffLimits()
) {
    companion object {
        fun default() = TariffConfig()
    }
}

@Serializable
data class TariffLimits(
    val instruments: List<String>? = null,
    val maxInstruments: Int = 1,
    val timeframes: List<String> = listOf("1m"),
    val maxHistoryHours: Int = 1,
    val maxTimeframe: String = "15m",
    val aggregation: List<String> = listOf("1x")
) {
    val isAllTimeframes: Boolean get() = timeframes.joinToString(",") == "all" || timeframes.contains("all")
    val isAllAggregation: Boolean get() = aggregation.joinToString(",") == "all" || aggregation.contains("all")
    val isUnlimitedHistory: Boolean get() = maxHistoryHours == -1

    companion object {
        fun default() = TariffLimits()
    }
}

data class BidaskerConfig(
    val baseUrl: String = "http://95.81.99.28:8085",
    val tariff: TariffConfig = TariffConfig.default(),
    val symbol: String = "BTCUSDT",
    val timeframe: String = "1m",
    val aggregation: String = "10x"
) {
    companion object {
        fun default() = BidaskerConfig()
    }
}

object TariffConfigParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonString: String): TariffConfig {
        return json.decodeFromString(jsonString)
    }
}
