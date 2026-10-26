/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.footprint

import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.FootprintLevel

/**
 * Чистая логика footprint: выбор источника таймфрейма и агрегация свечей.
 * Вынесено из companion ChartViewModel для тестируемости.
 */
object FootprintAggregator {

    /**
     * Возвращает исходный таймфрейм сервера и количество свечей для агрегации
     * в отображаемый таймфрейм.
     */
    fun resolveFootprintSourceTimeframe(displayTimeframe: String): Pair<String, Int> {
        return when (displayTimeframe) {
            "1m" -> "1m" to 1
            "5m" -> "5m" to 1
            "15m" -> "15m" to 1
            "30m" -> "15m" to 2
            "1h" -> "1h" to 1
            "4h" -> "4h" to 1
            "1d" -> "1d" to 1
            "1w" -> "1d" to 7
            else -> "1m" to 1
        }
    }

    /**
     * Длительность source-свечи в миллисекундах.
     */
    fun sourceTimeframeMs(sourceTimeframe: String): Long = when (sourceTimeframe) {
        "1m" -> 60_000L
        "5m" -> 300_000L
        "15m" -> 900_000L
        "30m" -> 1_800_000L
        "1h" -> 3_600_000L
        "4h" -> 14_400_000L
        "1d" -> 86_400_000L
        "1w" -> 604_800_000L
        else -> 60_000L
    }

    /**
     * Нижние таймфреймы, из которых можно собрать незакрытый display-бар.
     * Порядок — от крупного к мелкому: крупный источник закрывает максимум
     * диапазона, мелкие добирают хвост до текущего момента.
     */
    fun formingSourceTimeframes(displayTimeframe: String): List<String> = when (displayTimeframe) {
        "15m" -> listOf("5m", "1m")
        "30m" -> listOf("15m", "5m", "1m")
        "1h" -> listOf("15m", "5m", "1m")
        "4h" -> listOf("15m", "5m", "1m")
        "1d" -> listOf("4h", "1h", "15m", "5m", "1m")
        "1w" -> listOf("1d", "4h", "1h", "15m", "5m", "1m")
        else -> emptyList()
    }

    /**
     * Начало display-бакета (выравнивание по абсолютным границам времени).
     * Например, для 5m-бакетов (sourceMs=60_000, count=5) время 05:26 → 05:25.
     */
    fun bucketStart(timestampMs: Long, sourceMs: Long, count: Int): Long {
        val bucketMs = sourceMs * count
        return timestampMs / bucketMs * bucketMs
    }

    /**
     * Агрегирует свечи в бакеты по count source-свечей, выровненные по абсолютному
     * времени: startTime / (sourceMs * count). Это гарантирует, что границы
     * display-свечей не зависят от того, с какой свечи началась выборка.
     *
     * @param sourceMs длительность одной source-свечи в миллисекундах
     */
    fun aggregateFootprintCandles(
        candles: List<FootprintCandle>,
        count: Int,
        sourceMs: Long,
        outputTimeframe: String? = null,
    ): List<FootprintCandle> {
        if (count <= 1 || candles.isEmpty() || sourceMs <= 0L) return candles

        val bucketMs = sourceMs * count
        val groups = linkedMapOf<Long, MutableList<FootprintCandle>>()
        for (candle in candles) {
            val bucketStart = bucketStart(candle.startTime, sourceMs, count)
            groups.getOrPut(bucketStart) { mutableListOf() }.add(candle)
        }

        return groups.map { (groupStart, group) ->
            mergeCandles(
                candles = group,
                timeframe = outputTimeframe ?: group.first().timeframe,
                startTime = groupStart,
                endTime = groupStart + bucketMs,
            )
        }
    }

    /**
     * Собирает незакрытый display-бар из уже закрытых нижних таймфреймов.
     * Берёт максимально крупный источник, покрывающий начало бакета, затем
     * добирает оставшийся хвост более мелкими. Пропуск в источнике переключает
     * сборку на следующий, более мелкий таймфрейм.
     */
    fun assembleFormingCandle(
        displayTimeframe: String,
        bucketStart: Long,
        bucketEnd: Long,
        sourceCandles: Map<String, List<FootprintCandle>>,
    ): FootprintCandle? {
        if (bucketEnd <= bucketStart) return null

        val selected = mutableListOf<FootprintCandle>()
        var cursor = bucketStart

        for (sourceTimeframe in formingSourceTimeframes(displayTimeframe)) {
            val step = sourceTimeframeMs(sourceTimeframe)
            val bars = sourceCandles[sourceTimeframe].orEmpty().sortedBy { it.startTime }
            for (bar in bars) {
                if (bar.startTime < cursor) continue
                if (bar.startTime > cursor) break
                if (bar.endTime <= bar.startTime) continue
                if (bar.endTime > bucketEnd) break

                selected += bar
                cursor += step
                if (cursor >= bucketEnd) break
            }
            if (cursor >= bucketEnd) break
        }

        if (selected.isEmpty()) return null
        return mergeCandles(
            candles = selected,
            timeframe = displayTimeframe,
            startTime = bucketStart,
            endTime = bucketEnd,
        )
    }

    private data class Acc(
        var bidVol: Float = 0f,
        var askVol: Float = 0f,
        var bidCnt: Int = 0,
        var askCnt: Int = 0,
    )

    private fun mergeCandles(
        candles: List<FootprintCandle>,
        timeframe: String,
        startTime: Long,
        endTime: Long,
    ): FootprintCandle {
        val levelMap = linkedMapOf<String, Acc>()
        var totalTicks = 0L
        for (candle in candles) {
            totalTicks += candle.totalTicks
            for (level in candle.levels) {
                val acc = levelMap.getOrPut(level.price) { Acc() }
                acc.bidVol += level.bidVolumeFloat
                acc.askVol += level.askVolumeFloat
                acc.bidCnt += level.bidCount
                acc.askCnt += level.askCount
            }
        }

        val levelPrices = levelMap.keys.mapNotNull { it.toDoubleOrNull() }
        val sorted = levelMap.entries.sortedByDescending { it.key.toDoubleOrNull() ?: 0.0 }
        val levels = sorted.map { (price, acc) ->
            FootprintLevel(
                price = price,
                bidVolume = acc.bidVol.toString(),
                askVolume = acc.askVol.toString(),
                bidCount = acc.bidCnt,
                askCount = acc.askCnt,
            )
        }
        return FootprintCandle(
            exchange = candles.first().exchange,
            symbol = candles.first().symbol,
            timeframe = timeframe,
            startTime = startTime,
            endTime = endTime,
            totalTicks = totalTicks,
            minPrice = (levelPrices.minOrNull() ?: 0.0).toString(),
            maxPrice = (levelPrices.maxOrNull() ?: 0.0).toString(),
            levels = levels,
        )
    }
}
