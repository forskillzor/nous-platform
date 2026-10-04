/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance

import com.aandios.nous.api.market.ProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.parameter
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.concurrent.Volatile

/**
 * Единый хаб WebSocket-стримов Binance: мультиплексирует все подписки
 * (kline, depth, bookTicker, aggTrade, forceOrder) в минимум соединений
 * через combined streams (до [MAX_STREAMS_PER_CONNECTION] стримов на соединение).
 *
 * Зачем: каждый адаптер раньше открывал собственное соединение — workspace
 * с 8 графиками и 12 DOM-панелями создавал 25+ подключений и ловил
 * IP-бан 418 (300 попыток / 5 мин / IP → бан на 30 с).
 *
 * Поведение:
 * - refcounting подписчиков на стрим;
 * - изменение набора стримов → debounced ребилд соединений (батчинг всплесков);
 * - при ошибке/бане — единый cooldown и один реконнект на всех: потоки
 *   подписчиков не закрываются, панели не ретраят сами.
 */
class BinanceStreamHub(
    private val client: HttpClient,
    private val config: ProviderConfig,
) {
    companion object {
        private const val MAX_STREAMS_PER_CONNECTION = 200
        private const val REBUILD_DEBOUNCE_MS = 500L
        private const val DEFAULT_COOLDOWN_MS = 30_000L
        private const val MAX_BACKOFF_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()

    // streamName -> flow с сырым payload'ом (data-часть combined-сообщения)
    private val streams = mutableMapOf<String, MutableSharedFlow<String>>()
    private val subscriberCounts = mutableMapOf<String, Int>()

    // connectionId -> набор стримов
    private val connections = mutableMapOf<Long, Set<String>>()
    private val connectionJobs = mutableMapOf<Long, Job>()
    private var nextConnectionId = 0L
    private var rebuildJob: Job? = null

    @Volatile
    private var activeStreamCount = 0

    @Volatile
    private var activeConnectionCount = 0

    @Volatile
    private var cooldownUntil = 0L

    private var reconnectAttempts = 0

    /** Активных WS-соединений (для диагностики). */
    val connectionCount: Int get() = activeConnectionCount

    /** Активных стримов (для диагностики). */
    val streamCount: Int get() = activeStreamCount

    /**
     * Подписка на стрим по имени (например, "btcusdt@kline_1m").
     * Поток отдаёт только внутренний payload сообщения (data-часть).
     * Отписка — отмена сбора потока.
     */
    fun subscribe(streamName: String): Flow<String> = flow {
        val shared = mutex.withLock {
            val f = streams.getOrPut(streamName) {
                MutableSharedFlow(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            }
            subscriberCounts[streamName] = (subscriberCounts[streamName] ?: 0) + 1
            activeStreamCount = streams.size
            f
        }
        scheduleRebuild()
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
            mutex.withLock { activeStreamCount = streams.size }
            if (remaining == 0) scheduleRebuild()
        }
    }

    // ── Ребилд соединений ───────────────────────────────────────────────────

    private fun scheduleRebuild() {
        rebuildJob?.cancel()
        rebuildJob = scope.launch {
            delay(REBUILD_DEBOUNCE_MS)
            rebuildConnections()
        }
    }

    private suspend fun rebuildConnections() {
        val wanted = mutex.withLock { streams.keys.toList() }
        if (wanted.isEmpty()) {
            closeAllConnections()
            println("🔗 StreamHub: все стримы закрыты")
            return
        }
        // Жадное распределение стримов по соединениям (≤ MAX_STREAMS_PER_CONNECTION)
        val groups = mutableListOf<MutableList<String>>()
        wanted.forEach { name ->
            val group = groups.lastOrNull { it.size < MAX_STREAMS_PER_CONNECTION }
            if (group != null) group.add(name) else groups.add(mutableListOf(name))
        }

        mutex.withLock {
            val existing = connections.values.toList()
            val desired = groups.map { it.toSet() }
            val same = existing.size == desired.size &&
                    existing.zip(desired).all { (a, b) -> a == b }
            if (same) return

            closeAllConnectionsLocked()
            desired.forEach { group ->
                val id = nextConnectionId++
                connections[id] = group
                connectionJobs[id] = scope.launch { runConnection(id, group) }
            }
            activeConnectionCount = desired.size
        }
        println("🔗 StreamHub: ${groups.size} соединение(й), ${wanted.size} стримов")
    }

    private suspend fun closeAllConnections() {
        mutex.withLock { closeAllConnectionsLocked() }
    }

    private fun closeAllConnectionsLocked() {
        connectionJobs.values.forEach { it.cancel() }
        connectionJobs.clear()
        connections.clear()
        activeConnectionCount = 0
    }

    // ── Соединение ──────────────────────────────────────────────────────────

    private suspend fun runConnection(id: Long, names: Set<String>) {
        val base = if (config.isTestnet) "wss://testnet.binance.vision/stream"
        else "wss://fstream.binance.com/stream"
        val streamsParam = names.joinToString("/")

        while (coroutineContext.isActive) {
            val current = mutex.withLock { connections[id] } ?: return
            if (current.isEmpty()) return
            try {
                waitCooldown()
                client.webSocket(
                    urlString = base,
                    request = { parameter("streams", streamsParam) }
                ) {
                    mutex.withLock { reconnectAttempts = 0 }
                    println("🔗 StreamHub: соединение #$id установлено (${current.size} стримов)")
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            routeMessage(frame.readText())
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("🔗 StreamHub: соединение #$id упало: ${e.message}")
            }

            // Соединение закрылось или не установилось — cooldown + backoff с jitter
            val attempt = mutex.withLock { ++reconnectAttempts }
            val backoff = (1_000L shl (attempt - 1).coerceAtMost(5))
                .coerceAtMost(MAX_BACKOFF_MS)
                .let { it + Random.nextLong(0, it / 4 + 1) }
            markCooldown(backoff.coerceAtLeast(DEFAULT_COOLDOWN_MS / 3))
            println("🔗 StreamHub: реконнект #$id через ${cooldownUntil - currentTimeMillis()}ms")
        }
    }

    private suspend fun routeMessage(text: String) {
        try {
            val obj = json.decodeFromString<JsonElement>(text).jsonObject
            val streamName = obj["stream"]?.jsonPrimitive?.content ?: return
            val data = obj["data"] ?: return
            val payload = data.toString()
            mutex.withLock { streams[streamName]?.tryEmit(payload) }
        } catch (_: Exception) {
            // игнорируем битые сообщения
        }
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
