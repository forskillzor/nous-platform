/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.data.repository

import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.core.domain.repository.ChartRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

class ChartRepositoryImpl(
    private val chartAdapter: ChartAdapter,
): ChartRepository {

    private val realTimeUpdates = MutableStateFlow<Map<String, Flow<Candle>>>(emptyMap())

    override fun getChart(
        ticker: String,
        timeframe: String
    ): Flow<List<Candle>> = channelFlow {
        try {
            // Загружаем исторические данные
            val symbol = ticker.replace("/", "")
            val historicalCandles = loadHistoricalCandles(symbol, timeframe)

            // Отправляем исторические данные
            send(historicalCandles)

            // Подключаемся к real-time данным
            val realTimeFlow = getRealTimeUpdates(symbol, timeframe)

            // Объединяем потоки

            var currentCandles = historicalCandles

            realTimeFlow.collect { realTimeCandle ->
                currentCandles = updateCandleWithRealTime(
                    currentCandles = currentCandles,
                    newCandle = realTimeCandle,
                    timeframe = timeframe
                )
                send(currentCandles)
            }
        } catch (e: Exception) {
            close(e)
            throw e
        }
    }
    private suspend fun loadHistoricalCandles(symbol: String, timeframe: String): List<Candle> {
        val candles = chartAdapter.getCandles(
            symbol = symbol,
            interval = mapTimeframe(timeframe),
            limit = 200
        )
        return candles
    }
    private fun getRealTimeUpdates(symbol: String, interval: String): Flow<Candle> {
        val key = "$symbol-$interval"

        // Инициализируем кеш ДО flatMapLatest, чтобы избежать рестарта при модификации внутри lambda
        if (!realTimeUpdates.value.containsKey(key)) {
            realTimeUpdates.value = realTimeUpdates.value + (key to createRealTimeFlow(symbol, interval))
        }

        return realTimeUpdates.flatMapLatest { flows ->
            flows[key] ?: error("Real-time flow not initialized for key: $key")
        }
    }

    private fun createRealTimeFlow(symbol: String, interval: String): Flow<Candle>  =  flow {
        chartAdapter.subscribeToCandles(symbol, interval).collect { candle ->
            emit(candle)
        }
    }

    private fun updateCandleWithRealTime(
        currentCandles: List<Candle>,
        newCandle: Candle,
        timeframe: String
    ): List<Candle> {
        if (currentCandles.isEmpty()) return listOf(newCandle)

        val lastCandle = currentCandles.last()
        val timeframeMs = getTimeframeMillis(timeframe)

        val isSameCandle = newCandle.timestamp / timeframeMs == lastCandle.timestamp / timeframeMs

        return if( isSameCandle) {
            currentCandles.dropLast(1) + newCandle.copy(
                high = maxOf(lastCandle.high, newCandle.high),
                low = minOf(lastCandle.low, newCandle.low),
                close = newCandle.close,
                volume = lastCandle.volume + newCandle.volume
            )
        } else {
            currentCandles + newCandle
        }
    }

    private fun getTimeframeMillis(timeframe: String): Long {
        return when (timeframe) {
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

    override suspend fun loadHistoricalCandlesBefore(
        ticker: String,
        timeframe: String,
        endTime: Long,
        limit: Int
    ): List<Candle> {
        val symbol = ticker.replace("/", "")
        return chartAdapter.getCandlesBefore(symbol, mapTimeframe(timeframe), endTime, limit)
    }

    private fun mapTimeframe(timeframe: String): String {
        return when (timeframe) {
            "1m" -> "1m"
            "5m" -> "5m"
            "15m" -> "15m"
            "1h" -> "1h"
            "4h" -> "4h"
            "1d" -> "1d"
            else -> "1h"
        }
    }
}