/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.research.indicator.IndicatorEngine
import com.aandios.nous.research.indicator.IndicatorInstance
import com.aandios.nous.research.indicator.IndicatorOutput
import com.aandios.nous.research.indicator.IndicatorType
import com.aandios.nous.research.indicator.createIndicator
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndicatorComputePipelineTest {

    private fun candle(close: Float) = Candle(
        open = close,
        high = close,
        low = close,
        close = close,
        timestamp = 0L,
        volume = 1f,
    )

    private class RecordingEngine : IndicatorEngine {
        var computeCount = 0
        val closes = mutableListOf<Float>()

        override fun compute(
            candles: List<Candle>,
            instances: List<IndicatorInstance>,
        ): Map<String, IndicatorOutput> {
            computeCount++
            closes += candles.lastOrNull()?.close ?: 0f
            return instances
                .filter { it.type != IndicatorType.LIQUIDATIONS }
                .associate { instance ->
                    instance.id to IndicatorOutput(
                        instanceId = instance.id,
                        type = instance.type,
                        lines = emptyList(),
                    )
                }
        }
    }

    @Test
    fun `only newest queued request is computed`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = RecordingEngine()
        val pipeline = IndicatorComputePipeline(engine, this, dispatcher)
        try {
            val instance = createIndicator(IndicatorType.SMA, "sma-1")

            pipeline.submit(listOf(candle(1f)), listOf(instance))
            pipeline.submit(listOf(candle(2f)), listOf(instance))
            advanceUntilIdle()

            assertEquals(1, engine.computeCount)
            assertEquals(2f, engine.closes.single())
            assertEquals(setOf("sma-1"), pipeline.outputs.value.keys)
        } finally {
            pipeline.close()
        }
    }

    @Test
    fun `refresh before submit is a no-op`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = RecordingEngine()
        val pipeline = IndicatorComputePipeline(engine, this, dispatcher)
        try {
            pipeline.refresh()
            advanceUntilIdle()

            assertEquals(0, engine.computeCount)
        } finally {
            pipeline.close()
        }
    }

    @Test
    fun `refresh recomputes the last request`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = RecordingEngine()
        val pipeline = IndicatorComputePipeline(engine, this, dispatcher)
        try {
            val instance = createIndicator(IndicatorType.SMA, "sma-1")
            pipeline.submit(listOf(candle(1f)), listOf(instance))
            advanceUntilIdle()

            pipeline.refresh()
            advanceUntilIdle()

            assertEquals(2, engine.computeCount)
            assertEquals(listOf(1f, 1f), engine.closes)
        } finally {
            pipeline.close()
        }
    }

    @Test
    fun `many refreshes collapse into one queued compute`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = RecordingEngine()
        val pipeline = IndicatorComputePipeline(engine, this, dispatcher)
        try {
            val instance = createIndicator(IndicatorType.SMA, "sma-1")
            pipeline.submit(listOf(candle(1f)), listOf(instance))
            advanceUntilIdle()

            repeat(10) { pipeline.refresh() }
            advanceUntilIdle()

            // One compute may already be in flight plus one conflated request.
            assertTrue(engine.computeCount <= 3)
            assertTrue(engine.computeCount < 11)
        } finally {
            pipeline.close()
        }
    }

    @Test
    fun `empty request clears previous output`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = RecordingEngine()
        val pipeline = IndicatorComputePipeline(engine, this, dispatcher)
        try {
            val instance = createIndicator(IndicatorType.RSI, "rsi-1")

            pipeline.submit(listOf(candle(1f)), listOf(instance))
            advanceUntilIdle()
            assertTrue(pipeline.outputs.value.isNotEmpty())

            pipeline.submit(emptyList(), listOf(instance))
            advanceUntilIdle()
            assertTrue(pipeline.outputs.value.isEmpty())
        } finally {
            pipeline.close()
        }
    }
}
