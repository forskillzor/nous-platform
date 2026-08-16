/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.footprint

import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.FootprintLevel
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class FootprintApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://95.81.99.28:8085"
) {
    suspend fun getFootprint(
        exchange: String = "Binance",
        symbol: String,
        timeframe: String,
        from: Long? = null,
        to: Long? = null,
        limit: Int = 20
    ): List<FootprintCandle> {
        return httpClient.get("$baseUrl/api/footprint") {
            parameter("exchange", exchange)
            parameter("symbol", symbol)
            parameter("timeframe", timeframe)
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
            parameter("limit", limit)
        }.body()
    }

    suspend fun getCandleLevels(
        exchange: String = "Binance",
        symbol: String,
        timeframe: String,
        startTime: Long,
        endTime: Long
    ): List<FootprintLevel> {
        return httpClient.get("$baseUrl/api/footprint/$symbol/levels") {
            parameter("exchange", exchange)
            parameter("timeframe", timeframe)
            parameter("startTime", startTime)
            parameter("endTime", endTime)
        }.body()
    }

    suspend fun getInstruments(exchange: String = "Binance"): List<InstrumentSummary> {
        return httpClient.get("$baseUrl/api/instruments") {
            parameter("exchange", exchange)
        }.body()
    }
}

@kotlinx.serialization.Serializable
data class InstrumentSummary(
    val symbol: String,
    val start: Long,
    val end: Long,
    val candles: Long
)
