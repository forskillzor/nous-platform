/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.provider.mexc.MEXC_BASE_URL
import com.aandios.nous.provider.mexc.MexcRestGate
import com.aandios.nous.provider.mexc.MexcStreamHub
import com.aandios.nous.provider.mexc.MexcSubscriptions
import com.aandios.nous.provider.mexc.MexcTimeframes
import com.aandios.nous.provider.mexc.MexcWeights
import com.aandios.nous.provider.mexc.model.MexcKlinePush
import com.aandios.nous.provider.mexc.model.MexcKlineResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json

/**
 * Свечи MEXC Futures: REST `/api/v1/contract/kline/{symbol}` (параллельные
 * массивы) и WS `sub.kline` → `push.kline`.
 */
class MexcChartAdapter(
    private val client: HttpClient,
    @Suppress("unused") private val config: ProviderConfig,
    private val restGate: MexcRestGate,
    private val streamHub: MexcStreamHub,
) : ChartAdapter {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun getCandles(
        symbol: String,
        interval: String,
        limit: Int
    ): List<Candle> = restGate.execute(
        key = "kline:$symbol:$interval:$limit",
        weight = MexcWeights.KLINE,
    ) {
        val response = client.get("$MEXC_BASE_URL/api/v1/contract/kline/$symbol") {
            parameter("interval", MexcTimeframes.toMexc(interval))
        }.body<MexcKlineResponse>()
        // Без start/end сервер отдаёт последние 2000 баров — берём нужный хвост
        response.toCandles().takeLast(limit.coerceAtLeast(1))
    }

    override suspend fun getCandlesBefore(
        symbol: String,
        interval: String,
        endTime: Long,
        limit: Int
    ): List<Candle> = restGate.execute(
        key = "klineBefore:$symbol:$interval:$endTime:$limit",
        weight = MexcWeights.KLINE,
    ) {
        val response = client.get("$MEXC_BASE_URL/api/v1/contract/kline/$symbol") {
            parameter("interval", MexcTimeframes.toMexc(interval))
            parameter("end", endTime / 1000) // MEXC принимает end в секундах
        }.body<MexcKlineResponse>()
        response.toCandles().takeLast(limit.coerceAtLeast(1))
    }

    override fun subscribeToCandles(
        symbol: String,
        interval: String
    ): Flow<Candle> {
        val mexcInterval = MexcTimeframes.toMexc(interval)
        val sub = MexcSubscriptions.kline(symbol, mexcInterval)
        return streamHub.subscribe(sub).map { text ->
            json.decodeFromString<MexcKlinePush>(text).data?.toCandle()
        }.mapNotNull { it }
    }
}
