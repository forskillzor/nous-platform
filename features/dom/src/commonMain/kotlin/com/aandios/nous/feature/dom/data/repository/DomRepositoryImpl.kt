/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.data.repository

import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.model.orderbook.DepthSnapshot
import com.aandios.nous.api.market.model.orderbook.DomEvent
import com.aandios.nous.api.market.model.orderbook.OrderBookState
import com.aandios.nous.core.domain.repository.DomRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.pow

class DomRepositoryImpl(
    private val domAdapter: DomAdapter,
    private val bookTickerAdapter: BookTickerAdapter
) : DomRepository {
    /**
     * Подписывается на инкрементальные события DOM (стакана котировок).
     *
     * Реализует протокол синхронизации Binance:
     * 1. Открыть WebSocket стрим @depth и буферизировать все события
     * 2. Получить снапшот через REST
     * 3. Отбросить события где u < lastUpdateId
     * 4. Первое обработанное: U <= lastUpdateId+1 AND u >= lastUpdateId+1
     * 5. Каждое следующее: pu == предыдущее u
     * 6. Если pu != previous u — переинициализация с шага 1
     */
    override suspend fun subscribeToDomEvents(symbol: String, depth: Int): Flow<DomEvent> = callbackFlow {
        println("📊 Subscribing to $symbol DOM events with depth $depth")

        var reconnectAttempts = 0
        val maxReconnectAttempts = 5

        while (true) {
            try {
                val state = OrderBookState()

                // Шаг 1: Запускаем depth WebSocket и буферизируем события
                val depthJob = launch {
                    domAdapter.subscribeToDepthUpdates(symbol, depth)
                        .catch { e -> println("⚠️ Depth updates error: ${e.message}") }
                        .collect { depthUpdate ->
                            state.bufferEvent(depthUpdate)
                        }
                }

                // Даём время WebSocket подключиться и начать буферизацию
                delay(500)

                // Шаг 2: Получаем снапшот через REST
                val snapshot: DepthSnapshot = domAdapter.getOrderBookSnapshot(symbol, depth)
                state.updateFromSnapshot(snapshot)
                println("✅ Got snapshot for $symbol with lastUpdateId=${snapshot.lastUpdateId}, " +
                    "${state.bids.size} bids, ${state.asks.size} asks")

                // Отправляем событие Snapshot
                trySend(DomEvent.fromSnapshot(snapshot, symbol))

                // Шаг 3: Применяем буферизированные события с валидацией
                if (!state.flushPendingEvents()) {
                    println("⚠️ Order book sync failed during flush, re-initializing")
                    depthJob.cancel()
                    trySend(DomEvent.Reset)
                    continue
                }

                // Шаг 4: Продолжаем слушать depth (уже без буферизации)
                // и запускаем bookTicker параллельно
                val bookTickerJob = launch {
                    bookTickerAdapter.subscribeToBookTicker(symbol)
                        .catch { e -> println("⚠️ BestPrices error: ${e.message}") }
                        .collect { bookTicker ->
                            trySend(DomEvent.fromBookTicker(bookTicker, symbol))
                        }
                }

                // Переключаем depth на прямую валидацию (без буфера)
                depthJob.cancel()
                var reinitRequested = false

                val depthDirectJob = launch {
                    domAdapter.subscribeToDepthUpdates(symbol, depth)
                        .catch { e -> println("⚠️ Depth updates error: ${e.message}") }
                        .collect { depthUpdate ->
                            if (!state.applyUpdateWithValidation(depthUpdate)) {
                                println("⚠️ Binance order book sync failed for $symbol, re-initializing")
                                trySend(DomEvent.Reset)
                                reinitRequested = true
                                return@collect
                            }

                            // Без промежуточного List — callback напрямую
                            DomEvent.emitDepthUpdates(depthUpdate, symbol) { event ->
                                trySend(event)
                            }
                        }
                }

                // Ждём завершения любого из потоков
                bookTickerJob.join()
                depthDirectJob.join()

                if (reinitRequested) {
                    bookTickerJob.cancel()
                    depthDirectJob.cancel()
                    throw ReinitializationException("Order book sync failed for $symbol")
                }

                reconnectAttempts = 0

            } catch (e: ReinitializationException) {
                println("🔄 Re-initializing order book for $symbol: ${e.message}")
                reconnectAttempts = 0
                continue

            } catch (e: Exception) {
                println("❌ Error in $symbol DOM events: ${e.message}")
                e.printStackTrace()

                reconnectAttempts++
                if (reconnectAttempts > maxReconnectAttempts) {
                    println("❌ Max reconnection attempts reached for $symbol")
                    close(e)
                    break
                }

                val delayMs = (1000 * 2.0.pow(reconnectAttempts - 1.0)).toLong()
                println("⏳ Reconnecting to $symbol in ${delayMs}ms (attempt $reconnectAttempts)")
                delay(delayMs)

                trySend(DomEvent.Reset)
            }
        }

        close()
    }

    private class ReinitializationException(message: String) : Exception(message)
}
