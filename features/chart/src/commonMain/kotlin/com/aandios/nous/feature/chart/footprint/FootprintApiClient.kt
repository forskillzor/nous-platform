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
import io.ktor.http.ContentType
import io.ktor.http.contentType

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

    suspend fun getBatchFootprint(
        exchange: String = "Binance",
        symbol: String,
        queries: List<BatchFootprintQuery>,
    ): BatchFootprintResponse {
        return httpClient.post("$baseUrl/api/footprint/batch") {
            contentType(ContentType.Application.Json)
            setBody(
                BatchFootprintRequest(
                    exchange = exchange,
                    symbol = symbol,
                    queries = queries,
                )
            )
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

@kotlinx.serialization.Serializable
data class BatchFootprintQuery(
    val timeframe: String,
    val start: Long? = null,
    val end: Long? = null,
    val limit: Int = 20,
    val order: String = "desc",
)

@kotlinx.serialization.Serializable
data class BatchFootprintRequest(
    val exchange: String,
    val symbol: String,
    val queries: List<BatchFootprintQuery>,
)

@kotlinx.serialization.Serializable
data class BatchFootprintResult(
    val timeframe: String = "",
    val candles: List<FootprintCandle> = emptyList(),
)

@kotlinx.serialization.Serializable
data class BatchFootprintResponse(
    val exchange: String = "",
    val symbol: String = "",
    val results: List<BatchFootprintResult> = emptyList(),
)
