/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance

import com.aandios.nous.api.market.ProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.concurrent.Volatile

/**
 * Единый хаб WebSocket-стримов Binance: **одно соединение на уникальный
 * стрим** с refcounting подписчиков.
 *
 * Зачем: каждый адаптер раньше открывал собственное соединение — workspace
 * с 8 графиками и 12 DOM-панелями создавал 25+ подключений и ловил
 * IP-бан 418 (300 попыток / 5 мин / IP → бан на 30 с). Хаб схлопывает
 * дубликаты: N панелей на одном символе/глубине используют одно соединение
 * (в т.ч. глобальный `!forceOrder` между графиками).
 *
 * Примечание: combined streams (`/stream?streams=a/b`) на fstream не
 * используются намеренно — kline/aggTrade на этом эндпоинте молчат или
 * рвут соединение; raw `/ws/<stream>` и `/market/ws/<stream>` работают
 * стабильно (проверено диагностическим тестом).
 *
 * Поведение:
 * - refcounting подписчиков на стрим;
 * - при ошибке/бане — общий cooldown на новые подключения и per-stream
 *   backoff: панели не ретраят сами, потоки подписчиков не закрываются.
 */
class BinanceStreamHub(
    private val client: HttpClient,
    private val config: ProviderConfig,
) {
    companion object {
        private const val DEFAULT_COOLDOWN_MS = 30_000L
        private const val MAX_BACKOFF_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    // streamName -> flow с сырым payload'ом сообщения
    private val streams = mutableMapOf<String, MutableSharedFlow<String>>()
    private val subscriberCounts = mutableMapOf<String, Int>()
    private val connectionJobs = mutableMapOf<String, Job>()

    @Volatile
    private var activeConnectionCount = 0

    @Volatile
    private var activeStreamCount = 0

    @Volatile
    private var cooldownUntil = 0L

    /** Активных WS-соединений (для диагностики). */
    val connectionCount: Int get() = activeConnectionCount

    /** Активных стримов (для диагностики). */
    val streamCount: Int get() = activeStreamCount

    /**
     * Подписка на стрим по имени (например, "btcusdt@kline_1m").
     * Поток отдаёт сырой payload сообщения. Отписка — отмена сбора потока.
     */
    fun subscribe(streamName: String): Flow<String> = flow {
        val shared = mutex.withLock {
            val f = streams.getOrPut(streamName) {
                MutableSharedFlow(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            }
            subscriberCounts[streamName] = (subscriberCounts[streamName] ?: 0) + 1
            if (subscriberCounts[streamName] == 1) {
                connectionJobs[streamName] = scope.launch { runStreamConnection(streamName) }
                activeConnectionCount = connectionJobs.size
            }
            activeStreamCount = streams.size
            f
        }
        try {
            shared.collect { emit(it) }
        } finally {
            val remaining = mutex.withLock {
                val count = (subscriberCounts[streamName] ?: 1) - 1
                if (count <= 0) {
                    subscriberCounts.remove(streamName)
                    streams.remove(streamName)
                    0
                } else {
                    subscriberCounts[streamName] = count
                    count
                }
            }
            mutex.withLock {
                if (remaining == 0) {
                    connectionJobs.remove(streamName)?.cancel()
                }
                activeStreamCount = streams.size
                activeConnectionCount = connectionJobs.size
            }
        }
    }

    // ── Соединение ──────────────────────────────────────────────────────────

    private suspend fun runStreamConnection(streamName: String) {
        val endpoint = if (config.isTestnet) {
            "wss://testnet.binance.vision/ws/$streamName"
        } else {
            // Старый проверенный формат: depth/bookTicker — /ws, остальные — /market/ws
            if (streamName.contains("@bookTicker") || streamName.contains("@depth")) {
                "wss://fstream.binance.com/ws/$streamName"
            } else {
                "wss://fstream.binance.com/market/ws/$streamName"
            }
        }

        var attempt = 0
        while (coroutineContext.isActive) {
            val stillSubscribed = mutex.withLock { connectionJobs[streamName] != null }
            if (!stillSubscribed) return
            try {
                waitCooldown()
                client.webSocket(urlString = endpoint) {
                    attempt = 0
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            routeMessage(streamName, frame.readText())
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("🔗 StreamHub: стрим $streamName упал: ${e.message}")
            }

            // Соединение закрылось или не установилось — backoff с jitter
            attempt++
            val backoff = (1_000L shl (attempt - 1).coerceAtMost(5))
                .coerceAtMost(MAX_BACKOFF_MS)
                .let { it + Random.nextLong(0, it / 4 + 1) }
            markCooldown(backoff.coerceAtLeast(DEFAULT_COOLDOWN_MS / 3))
        }
    }

    private suspend fun routeMessage(streamName: String, text: String) {
        mutex.withLock { streams[streamName]?.tryEmit(text) }
    }

    // ── Cooldown ────────────────────────────────────────────────────────────

    /** Запрет подключений на [ms] (не укорачивает уже установленный cooldown). */
    private suspend fun markCooldown(ms: Long) {
        val target = currentTimeMillis() + ms
        mutex.withLock {
            if (target > cooldownUntil) cooldownUntil = target
        }
    }

    private suspend fun waitCooldown() {
        while (true) {
            val remaining = cooldownUntil - currentTimeMillis()
            if (remaining <= 0) return
            delay(remaining.coerceAtLeast(50))
        }
    }
}
