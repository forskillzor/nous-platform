/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.data.repository

import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.model.orderbook.DomEvent
import com.aandios.nous.core.domain.repository.DomRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.math.pow

/**
 * Подписка на окна стакана (partial-стрим) + лучшие цены.
 *
 * Инкрементальной синхронизации нет: каждое окно полностью заменяет книгу.
 * Если один из стримов оборвался — оба переподписываются с экспоненциальным
 * backoff; при реконнекте следующее окно само восстановит книгу.
 */
class DomRepositoryImpl(
    private val domAdapter: DomAdapter,
    private val bookTickerAdapter: BookTickerAdapter
) : DomRepository {

    override suspend fun subscribeToDomEvents(symbol: String, depth: Int): Flow<DomEvent> = callbackFlow {
        println("📊 Subscribing to $symbol book windows with depth $depth")

        var attempt = 0

        while (true) {
            try {
                val windowJob = launch {
                    domAdapter.subscribeToBookWindow(symbol, depth)
                        .catch { e -> println("⚠️ Book window stream error: ${e.message}") }
                        .collect { window ->
                            trySend(DomEvent.fromWindow(window))
                        }
                }
                val tickerJob = launch {
                    bookTickerAdapter.subscribeToBookTicker(symbol)
                        .catch { e -> println("⚠️ BestPrices stream error: ${e.message}") }
                        .collect { bookTicker ->
                            trySend(DomEvent.fromBookTicker(bookTicker, symbol))
                        }
                }

                select {
                    windowJob.onJoin { }
                    tickerJob.onJoin { }
                }
                windowJob.cancel()
                tickerJob.cancel()

                attempt++
                val delayMs = (250L * 2.0.pow((attempt - 1).coerceAtMost(5))).toLong().coerceAtMost(8000L)
                println("⏳ Reconnecting $symbol book streams in ${delayMs}ms (attempt $attempt)")
                delay(delayMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("❌ Error in $symbol DOM events: ${e.message}")
                close(e)
                break
            }
        }

        close()
    }
}
