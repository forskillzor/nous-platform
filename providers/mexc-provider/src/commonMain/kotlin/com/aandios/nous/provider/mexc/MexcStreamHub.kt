/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random
import kotlin.concurrent.Volatile

/**
 * Хаб WebSocket-стримов MEXC Futures: **одно соединение** на весь провайдер
 * (`wss://contract.mexc.com/edge`) с мультиплексированием каналов и
 * refcounting подписчиков.
 *
 * В отличие от Binance (отдельное соединение на стрим), MEXC принимает
 * неограниченное число подписок на одном соединении — хаб сам рассылает
 * sub/unsub по refcount'у и переподписывает все активные каналы после
 * реконнекта. Пинг приложения `{"method":"ping"}` каждые 15 с — сервер
 * рвёт соединение без пинга за 60 с.
 */
class MexcStreamHub(
    private val client: HttpClient,
    @Suppress("unused") private val config: com.aandios.nous.api.market.ProviderConfig,
) {
    companion object {
        private const val ENDPOINT = "wss://contract.mexc.com/edge"
        private const val PING_INTERVAL_MS = 15_000L
        private const val DEFAULT_COOLDOWN_MS = 10_000L
        private const val MAX_BACKOFF_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // key -> flow с сырым payload'ом сообщения
    private val streams = mutableMapOf<String, MutableSharedFlow<String>>()
    private val subscriberCounts = mutableMapOf<String, Int>()
    private val subscriptions = mutableMapOf<String, MexcSub>()

    /** Исходящие сообщения (sub/unsub/ping) для активного соединения. */
    private val outbox = Channel<String>(capacity = Channel.UNLIMITED)

    private var connectionJob: Job? = null

    @Volatile
    private var activeConnectionCount = 0

    @Volatile
    private var activeStreamCount = 0

    @Volatile
    private var cooldownUntil = 0L

    /** Активных WS-соединений (для диагностики). */
    val connectionCount: Int get() = activeConnectionCount

    /** Активных каналов (для диагностики). */
    val streamCount: Int get() = activeStreamCount

    /**
     * Подписка на канал. Поток отдаёт сырой payload push-сообщения.
     * Отписка — отмена сбора потока.
     */
    fun subscribe(sub: MexcSub): Flow<String> = flow {
        val shared = mutex.withLock {
            val f = streams.getOrPut(sub.key) {
                MutableSharedFlow(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            }
            subscriberCounts[sub.key] = (subscriberCounts[sub.key] ?: 0) + 1
            if (subscriberCounts[sub.key] == 1) {
                subscriptions[sub.key] = sub
                outbox.trySend(sub.subscribeJson)
                ensureConnection()
            }
            activeStreamCount = streams.size
            f
        }
        try {
            shared.collect { emit(it) }
        } finally {
            val remaining = mutex.withLock {
                val count = (subscriberCounts[sub.key] ?: 1) - 1
                if (count <= 0) {
                    subscriberCounts.remove(sub.key)
                    streams.remove(sub.key)
                    0
                } else {
                    subscriberCounts[sub.key] = count
                    count
                }
            }
            mutex.withLock {
                if (remaining == 0) {
                    subscriptions.remove(sub.key)
                    outbox.trySend(sub.unsubscribeJson)
                }
                activeStreamCount = streams.size
            }
        }
    }

    // ── Соединение ──────────────────────────────────────────────────────────

    private fun ensureConnection() {
        if (connectionJob?.isActive == true) return
        connectionJob = scope.launch { runConnection() }
    }

    private suspend fun runConnection() {
        var attempt = 0
        while (coroutineContext.isActive) {
            val hasSubscribers = mutex.withLock { subscriptions.isNotEmpty() }
            if (!hasSubscribers) {
                connectionJob = null
                activeConnectionCount = 0
                return
            }
            try {
                waitCooldown()
                client.webSocket(urlString = ENDPOINT) {
                    attempt = 0
                    activeConnectionCount = 1

                    // Переподписка всех активных каналов после (пере)подключения
                    mutex.withLock {
                        subscriptions.values.forEach { outbox.trySend(it.subscribeJson) }
                    }

                    val pingJob = launch {
                        while (isActive) {
                            delay(PING_INTERVAL_MS)
                            outbox.trySend(MexcSubscriptions.PING)
                        }
                    }
                    val sendJob = launch {
                        for (msg in outbox) {
                            send(Frame.Text(msg))
                        }
                    }
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                routeMessage(frame.readText())
                            }
                        }
                    } finally {
                        pingJob.cancel()
                        sendJob.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("🔗 MexcStreamHub: соединение упало: ${e.message}")
            }

            activeConnectionCount = 0
            attempt++
            val backoff = (1_000L shl (attempt - 1).coerceAtMost(5))
                .coerceAtMost(MAX_BACKOFF_MS)
                .let { it + Random.nextLong(0, it / 4 + 1) }
            markCooldown(backoff.coerceAtLeast(DEFAULT_COOLDOWN_MS))
        }
    }

    /**
     * Маршрутизация входящего push-сообщения по каналу/symbol/interval
     * в потоки подписчиков.
     */
    private suspend fun routeMessage(text: String) {
        val obj = try {
            json.parseToJsonElement(text) as? JsonObject ?: return
        } catch (_: Exception) {
            return
        }
        val channel = (obj["channel"] as? JsonPrimitive)?.content ?: return
        val symbol = (obj["symbol"] as? JsonPrimitive)?.content.orEmpty()
        val data = obj["data"] as? JsonObject

        val keys = when (channel) {
            "push.kline" -> {
                val interval = (data?.get("interval") as? JsonPrimitive)?.content.orEmpty()
                listOf("kline:$symbol:$interval")
            }
            "push.deal" -> listOf("deal:$symbol")
            "push.depth", "push.depth.full" -> listOf("depth:$symbol")
            "push.ticker" -> listOf("ticker:$symbol")
            else -> emptyList()
        }
        if (keys.isEmpty()) return
        mutex.withLock {
            keys.forEach { key -> streams[key]?.tryEmit(text) }
        }
    }

    // ── Cooldown ────────────────────────────────────────────────────────────

    private fun markCooldown(ms: Long) {
        val target = currentTimeMillis() + ms
        if (target > cooldownUntil) cooldownUntil = target
    }

    private suspend fun waitCooldown() {
        while (true) {
            val remaining = cooldownUntil - currentTimeMillis()
            if (remaining <= 0) return
            delay(remaining.coerceAtLeast(50))
        }
    }
}
