/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.research.indicator.IndicatorEngine
import com.aandios.nous.research.indicator.IndicatorInstance
import com.aandios.nous.research.indicator.IndicatorOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Serializes indicator computation off the UI thread. Only the newest request
 * is kept while a compute is running, so live candle ticks cannot pile up
 * canceled-but-still-running TA4J work.
 */
class IndicatorComputePipeline(
    private val engine: IndicatorEngine,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    data class Request(
        val candles: List<Candle>,
        val instances: List<IndicatorInstance>,
    )

    private val _outputs = MutableStateFlow<Map<String, IndicatorOutput>>(emptyMap())
    val outputs: StateFlow<Map<String, IndicatorOutput>> = _outputs.asStateFlow()

    private val requests = Channel<Request>(Channel.CONFLATED)

    private val worker: Job = scope.launch(
        dispatcher,
        start = CoroutineStart.LAZY,
    ) {
        for (request in requests) {
            if (request.candles.isEmpty() || request.instances.isEmpty()) {
                _outputs.value = emptyMap()
                continue
            }
            try {
                _outputs.value = engine.compute(request.candles, request.instances)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Keep the last good output; the next request retries.
            }
        }
    }

    init {
        worker.start()
    }

    fun submit(candles: List<Candle>, instances: List<IndicatorInstance>) {
        requests.trySend(Request(candles, instances))
    }

    fun close() {
        requests.close()
        worker.cancel()
    }
}
