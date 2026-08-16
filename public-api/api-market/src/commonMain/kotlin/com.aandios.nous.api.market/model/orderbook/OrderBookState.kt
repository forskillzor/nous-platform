/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.orderbook

/**
 * Состояние локального стакана котировок с валидацией последовательности Binance.
 *
 * Binance требует следующего протокола синхронизации:
 * 1. Получить снапшот через REST (содержит lastUpdateId)
 * 2. Буферизировать все WebSocket события, полученные ДО и ПОСЛЕ снапшота
 * 3. Отбросить события где finalUpdateId < lastUpdateId (были до снапшота)
 * 4. Первое обработанное событие: U <= lastUpdateId+1 AND u >= lastUpdateId+1
 * 5. Каждое следующее: previousFinalUpdateId == предыдущее finalUpdateId
 * 6. Если проверка pu не пройдена — переинициализация (новый снапшот)
 */
class OrderBookState {
    val bids = concurrentMap<String, String>()  // цена -> объем
    val asks = concurrentMap<String, String>()  // цена -> объем
    var lastUpdateId: Long = 0
        private set

    /**
     * Флаг: true после успешной инициализации (снапшот + первое валидное событие).
     */
    private var isInitialized = false

    /**
     * Буфер событий depth, полученных до применения снапшота.
     * Используется для реализации протокола Binance:
     * 1. Открыть WebSocket и буферизировать все события
     * 2. Получить снапшот через REST
     * 3. Применить буферизированные события с валидацией
     */
    private val pendingEvents = mutableListOf<DepthUpdate>()

    /**
     * Обновляет состояние из снапшота.
     */
    fun updateFromSnapshot(snapshot: DepthSnapshot) {
        bids.clear()
        asks.clear()

        snapshot.bids.forEach { (price, qty) ->
            if (qty.toDoubleOrNull() != 0.0) bids[price] = qty
        }
        snapshot.asks.forEach { (price, qty) ->
            if (qty.toDoubleOrNull() != 0.0) asks[price] = qty
        }

        lastUpdateId = snapshot.lastUpdateId
        isInitialized = false
    }

    /**
     * Применяет DepthUpdate с валидацией последовательности Binance.
     *
     * По спецификации Binance:
     * - Если u < lastUpdateId: событие устарело, пропускаем
     * - Если U <= lastUpdateId+1 AND u >= lastUpdateId+1: первое валидное событие
     * - Если U > lastUpdateId+1: пропущены события — это НЕ ошибка, просто ждём следующее
     * - После инициализации: pu == lastUpdateId (предыдущее u)
     *
     * @return true если событие успешно применено или пропущено, false если нужна переинициализация
     */
    fun applyUpdateWithValidation(update: DepthUpdate): Boolean {
        if (!isInitialized) {
            // Событие устарело (было до снапшота) — пропускаем
            if (update.finalUpdateId < lastUpdateId) {
                return true
            }

            // Первое событие после снапшота: U <= lastUpdateId+1 AND u >= lastUpdateId+1
            if (update.firstUpdateId <= lastUpdateId + 1 && update.finalUpdateId >= lastUpdateId + 1) {
                applyUpdateData(update)
                isInitialized = true
                return true
            }

            // U > lastUpdateId+1 — пропущены события между снапшотом и этим событием.
            // Это нормально для Binance, просто ждём следующее событие.
//            println("⚠️ OrderBookState: gap detected before snapshot, skipping event. " +
//                "lastUpdateId=$lastUpdateId, U=${update.firstUpdateId}, u=${update.finalUpdateId}")
            return true
        }

        // Проверка pu == previous u
        if (update.previousFinalUpdateId != lastUpdateId) {
            println("⚠️ OrderBookState: sequence break detected. " +
                "expected pu=$lastUpdateId, got pu=${update.previousFinalUpdateId}, " +
                "u=${update.finalUpdateId}")
            isInitialized = false
            return false
        }

        applyUpdateData(update)
        return true
    }

    private fun applyUpdateData(update: DepthUpdate) {
        // Обновляем bids
        update.bids.forEach { (price, qty) ->
            if (qty.toDoubleOrNull() == 0.0) {
                bids.remove(price)
            } else {
                bids[price] = qty
            }
        }

        // Обновляем asks
        update.asks.forEach { (price, qty) ->
            if (qty.toDoubleOrNull() == 0.0) {
                asks.remove(price)
            } else {
                asks[price] = qty
            }
        }

        lastUpdateId = update.finalUpdateId
    }

    /**
     * Буферизирует событие depth до применения снапшота.
     * Используется на шаге 1 протокола Binance.
     */
    fun bufferEvent(update: DepthUpdate) {
        pendingEvents.add(update)
    }

    /**
     * Применяет буферизированные события после получения снапшота.
     * Реализует шаги 3-5 протокола Binance:
     * - Отбрасывает события где u < lastUpdateId
     * - Первое валидное: U <= lastUpdateId+1 AND u >= lastUpdateId+1
     * - Каждое следующее: pu == предыдущее u
     *
     * @return true если синхронизация успешна, false если нужна переинициализация
     */
    fun flushPendingEvents(): Boolean {
        val eventsToProcess = pendingEvents.toList()
        pendingEvents.clear()

        for (event in eventsToProcess) {
            if (!applyUpdateWithValidation(event)) {
                // Синхронизация нарушена — возвращаем события обратно в буфер
                // для следующей попытки
                pendingEvents.addAll(eventsToProcess.drop(eventsToProcess.indexOf(event) + 1))
                return false
            }
        }

        return true
    }

    /**
     * Устаревший метод. Используйте [applyUpdateWithValidation] для корректной синхронизации.
     */
    @Deprecated("Use applyUpdateWithValidation() for proper Binance order book sync")
    fun applyUpdate(update: DepthUpdate) {
        applyUpdateData(update)
    }

    /**
     * Получить bids, обрезанные по лучшей цене
     */
    fun getBidsUpToBestBid(bestBid: Double): List<OrderBookLevel> {
        return bids
            .filter { it.key.toDouble() <= bestBid }
            .map { OrderBookLevel(it.key, it.value) }
            .sortedByDescending { it.price.toDouble() }
    }

    /**
     * Получить asks, обрезанные по лучшей цене
     */
    fun getAsksFromBestAsk(bestAsk: Double): List<OrderBookLevel> {
        return asks
            .filter { it.key.toDouble() >= bestAsk }
            .map { OrderBookLevel(it.key, it.value) }
            .sortedBy { it.price.toDouble() }
    }
}
