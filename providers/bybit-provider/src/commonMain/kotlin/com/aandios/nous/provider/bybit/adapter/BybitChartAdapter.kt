/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit.adapter

import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.provider.bybit.BybitRestClient
import com.aandios.nous.provider.bybit.BybitStreamHub
import com.aandios.nous.provider.bybit.asArray
import com.aandios.nous.provider.bybit.asObject
import com.aandios.nous.provider.bybit.bybitInterval
import com.aandios.nous.provider.bybit.bybitKline
import com.aandios.nous.provider.bybit.string
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json

class BybitChartAdapter internal constructor(
    private val rest: BybitRestClient,
    private val streams: BybitStreamHub,
) : ChartAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getCandles(symbol: String, interval: String, limit: Int): List<Candle> =
        load(symbol, interval, limit, null)

    override suspend fun getCandlesBefore(
        symbol: String,
        interval: String,
        endTime: Long,
        limit: Int,
    ): List<Candle> = load(symbol, interval, limit, endTime)

    private suspend fun load(symbol: String, interval: String, limit: Int, endTime: Long?): List<Candle> {
        val params = mutableMapOf(
            "category" to "linear",
            "symbol" to symbol,
            "interval" to bybitInterval(interval),
            "limit" to limit.coerceIn(1, 1_000).toString(),
        )
        endTime?.let { params["end"] = it.toString() }
        val result = rest.get("/v5/market/kline", params)
        return result["list"]?.asArray().orEmpty()
            .mapNotNull { it.asArray()?.let(::bybitKline) }
            .sortedBy { it.timestamp }
    }

    override fun subscribeToCandles(symbol: String, interval: String): Flow<Candle> {
        val topic = "kline.${bybitInterval(interval)}.$symbol"
        return streams.subscribe(topic).mapNotNull { text ->
            val root = json.parseToJsonElement(text).asObject() ?: return@mapNotNull null
            if (root.string("topic") != topic) return@mapNotNull null
            val data = root["data"]?.asArray()?.firstOrNull() ?: return@mapNotNull null
            data.asObject()?.let(::bybitKline)
        }
    }
}
