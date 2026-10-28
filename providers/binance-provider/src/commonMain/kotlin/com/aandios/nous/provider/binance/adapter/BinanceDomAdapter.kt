/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.provider.binance.BinanceDepthBook
import com.aandios.nous.provider.binance.BinanceRestGate
import com.aandios.nous.provider.binance.BinanceStreamHub
import com.aandios.nous.provider.binance.currentTimeMillis
import com.aandios.nous.provider.binance.model.BBookWindow
import com.aandios.nous.provider.binance.model.BinanceDepthSnapshot
import com.aandios.nous.provider.binance.model.BinanceDepthUpdate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Binance DOM Adapter.
 *
 * Глубина подписки приходит уже с учётом агрегации (`depth × agg`):
 *  * 5/10/20 — нативный partial-стрим `depth<levels>@100ms` (как раньше);
 *  * больше 20 — diff-стрим `@depth@100ms` + REST-снапшот и локальная
 *    [BinanceDepthBook]. В UI, как и раньше, отдаём самодостаточные окна
 *    топ-N с абсолютными объёмами — потребитель не знает про diff.
 *
 * Стримы мультиплексируются через общий [BinanceStreamHub] — N панелей
 * на одном символе/глубине используют одно соединение.
 */
class BinanceDomAdapter(
    private val client: HttpClient,
    private val config: ProviderConfig,
    private val streamHub: BinanceStreamHub,
    private val restGate: BinanceRestGate,
) : DomAdapter {

    companion object {
        private val PARTIAL_LEVELS = setOf(5, 10, 20)

        /** Максимум сырых уровней: REST depth Binance не отдаёт больше 1000. */
        private const val MAX_DEPTH = 1000

        /** Окно в UI — не чаще, чем раз в 100 мс (как partial-стрим). */
        private const val WINDOW_INTERVAL_MS = 100L

        /** Тик добора «хвоста»: применили диффы, но окно ещё не отдавали. */
        private const val TAIL_TICK_MS = 50L

        /** Не чаще одного ресинка в секунду, если REST-снапшот не отвечает. */
        private const val RESYNC_MIN_INTERVAL_MS = 1_000L
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun subscribeToBookWindow(symbol: String, depth: Int): Flow<BookWindowLevels> =
        if (depth in PARTIAL_LEVELS) {
            subscribePartial(symbol, depth)
        } else {
            subscribeDiff(symbol, depth.coerceIn(1, MAX_DEPTH))
        }

    /** Нативный partial-стрим: окно уже самодостаточно. */
    private fun subscribePartial(symbol: String, depth: Int): Flow<BookWindowLevels> {
        val streamName = "${symbol.lowercase()}@depth$depth@100ms"
        return streamHub.subscribe(streamName).map { text ->
            json.decodeFromString<BBookWindow>(text).toBookWindowLevels()
        }
    }

    /** Diff-стрим + локальная книга: в UI отдаём полные окна топ-N. */
    private fun subscribeDiff(symbol: String, depth: Int): Flow<BookWindowLevels> = channelFlow {
        val book = BinanceDepthBook()
        val raw = Channel<BinanceDepthUpdate>(Channel.UNLIMITED)

        // Подписываемся ДО снапшота, чтобы не потерять события: лишние
        // стейл-события отбросит книга по lastUpdateId.
        launch {
            streamHub.subscribe("${symbol.lowercase()}@depth@100ms").collect { text ->
                runCatching { json.decodeFromString<BinanceDepthUpdate>(text) }
                    .getOrNull()
                    ?.let { raw.trySend(it) }
            }
        }

        var lastEmit = 0L
        fetchDepthSnapshot(symbol, depth)?.let { snapshot ->
            book.reset(
                bids = BookWindowLevels.parseRows(snapshot.bids),
                asks = BookWindowLevels.parseRows(snapshot.asks),
                lastUpdateId = snapshot.lastUpdateId,
            )
            send(book.window(depth))
            lastEmit = currentTimeMillis()
        }

        var dirty = false
        var lastResyncAt = 0L
        while (isActive) {
            val push = withTimeoutOrNull(TAIL_TICK_MS) { raw.receive() }
            if (push != null) {
                when (
                    book.apply(
                        bids = BookWindowLevels.parseRows(push.bids),
                        asks = BookWindowLevels.parseRows(push.asks),
                        firstUpdateId = push.firstUpdateId,
                        finalUpdateId = push.finalUpdateId,
                        previousFinalUpdateId = push.previousFinalUpdateId,
                    )
                ) {
                    BinanceDepthBook.ApplyResult.APPLIED -> {
                        val now = currentTimeMillis()
                        if (now - lastEmit >= WINDOW_INTERVAL_MS) {
                            send(book.window(depth))
                            lastEmit = now
                            dirty = false
                        } else {
                            dirty = true
                        }
                    }

                    BinanceDepthBook.ApplyResult.IGNORED -> Unit

                    BinanceDepthBook.ApplyResult.GAP -> {
                        // Потеря последовательности — новый снапшот и продолжаем.
                        val now = currentTimeMillis()
                        if (now - lastResyncAt >= RESYNC_MIN_INTERVAL_MS) {
                            lastResyncAt = now
                            fetchDepthSnapshot(symbol, depth)?.let { snapshot ->
                                book.reset(
                                    bids = BookWindowLevels.parseRows(snapshot.bids),
                                    asks = BookWindowLevels.parseRows(snapshot.asks),
                                    lastUpdateId = snapshot.lastUpdateId,
                                )
                                send(book.window(depth))
                                lastEmit = currentTimeMillis()
                                dirty = false
                            }
                        }
                    }
                }
            }
            // Хвостовой эмит: диффы затихли — доставляем последнее состояние.
            if (dirty && currentTimeMillis() - lastEmit >= WINDOW_INTERVAL_MS) {
                send(book.window(depth))
                lastEmit = currentTimeMillis()
                dirty = false
            }
        }
    }

    /** REST-снапшот книги через общий gate (вес зависит от limit). */
    internal suspend fun fetchDepthSnapshot(symbol: String, depth: Int): BinanceDepthSnapshot? {
        val limit = snapshotLimit(depth)
        return runCatching {
            restGate.execute(key = "dom:depth:$symbol:$limit", weight = snapshotWeight(limit)) {
                client.get("${baseUrl()}/fapi/v1/depth") {
                    parameter("symbol", symbol)
                    parameter("limit", limit)
                }.body<BinanceDepthSnapshot>()
            }
        }.getOrNull()
    }

    private fun snapshotLimit(depth: Int): Int = when {
        depth <= 50 -> 50
        depth <= 100 -> 100
        depth <= 500 -> 500
        else -> 1000
    }

    private fun snapshotWeight(limit: Int): Int = when {
        limit <= 50 -> 2
        limit <= 100 -> 5
        limit <= 500 -> 10
        else -> 20
    }

    private fun baseUrl(): String = if (config.isTestnet) {
        "https://testnet.binancefuture.com"
    } else {
        "https://fapi.binance.com"
    }
}
