/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid.adapter

import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.provider.hyperliquid.HyperliquidInfoApi
import com.aandios.nous.provider.hyperliquid.HyperliquidMarketData
import com.aandios.nous.provider.hyperliquid.HyperliquidStreamApi
import com.aandios.nous.provider.hyperliquid.asArray
import com.aandios.nous.provider.hyperliquid.asObject
import com.aandios.nous.provider.hyperliquid.hyperliquidCurrentTimeMillis
import com.aandios.nous.provider.hyperliquid.hyperliquidCandle
import com.aandios.nous.provider.hyperliquid.hyperliquidInterval
import com.aandios.nous.provider.hyperliquid.hyperliquidIntervalMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HyperliquidChartAdapter internal constructor(
    private val info: HyperliquidInfoApi,
    private val streams: HyperliquidStreamApi,
    private val marketData: HyperliquidMarketData,
    private val clock: () -> Long = { hyperliquidCurrentTimeMillis() },
) : ChartAdapter {

    override suspend fun getCandles(symbol: String, interval: String, limit: Int): List<Candle> {
        val count = limit.coerceIn(1, MAX_CANDLES)
        val end = clock()
        val start = end - count.toLong() * hyperliquidIntervalMillis(interval)
        return load(symbol, interval, start, end).takeLast(count)
    }

    override suspend fun getCandlesBefore(
        symbol: String,
        interval: String,
        endTime: Long,
        limit: Int,
    ): List<Candle> {
        val count = limit.coerceIn(1, MAX_CANDLES)
        val end = (endTime - 1L).coerceAtLeast(0L)
        val start = (end - (count.toLong() + 1L) * hyperliquidIntervalMillis(interval)).coerceAtLeast(0L)
        return load(symbol, interval, start, end)
            .filter { it.timestamp < endTime }
            .takeLast(count)
    }

    override fun subscribeToCandles(symbol: String, interval: String): Flow<Candle> {
        val coin = marketData.knownCoin(symbol) ?: symbol
        val exchangeInterval = hyperliquidInterval(interval)
        val key = "candle|$coin|$exchangeInterval"
        val subscription = buildJsonObject {
            put("type", "candle")
            put("coin", coin)
            put("interval", exchangeInterval)
        }
        return flow {
            var lastTimestamp = 0L
            streams.subscribe(key, subscription).collect { root ->
                val candle = root["data"]?.asArray()
                    ?.mapNotNull { it.asObject()?.let(::hyperliquidCandle) }
                    ?.maxByOrNull { it.timestamp }
                    ?: return@collect
                if (candle.timestamp < lastTimestamp) return@collect
                lastTimestamp = candle.timestamp
                emit(candle)
            }
        }
    }

    private suspend fun load(
        symbol: String,
        interval: String,
        startTime: Long,
        endTime: Long,
    ): List<Candle> {
        val coin = marketData.canonicalCoinOrNull(symbol) ?: symbol
        val body = buildJsonObject {
            put("type", "candleSnapshot")
            put("req", buildJsonObject {
                put("coin", coin)
                put("interval", hyperliquidInterval(interval))
                put("startTime", startTime)
                put("endTime", endTime)
            })
        }
        val response = info.info(body)
        return response.asArray().orEmpty()
            .mapNotNull { it.asObject()?.let(::hyperliquidCandle) }
            .sortedBy { it.timestamp }
    }

    private companion object {
        const val MAX_CANDLES = 5_000
    }
}
