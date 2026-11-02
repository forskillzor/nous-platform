/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

internal interface HyperliquidStreamApi {
    fun subscribe(key: String, subscription: JsonObject): Flow<JsonObject>
    val errors: SharedFlow<String>
}

internal class HyperliquidStreamHub(
    private val client: HttpClient,
    private val wsUrl: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : HyperliquidStreamApi {
    private data class Subscription(
        val message: JsonObject,
        val flow: MutableSharedFlow<JsonObject>,
        var count: Int,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()
    private val subscriptions = mutableMapOf<String, Subscription>()
    private val outbound = Channel<String>(Channel.UNLIMITED)
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val errors: SharedFlow<String> = _errors.asSharedFlow()
    private var started = false
    private var connectionJob: Job? = null

    override fun subscribe(key: String, subscription: JsonObject): Flow<JsonObject> = flow {
        val shared = mutex.withLock {
            val existing = subscriptions[key]
            if (existing != null) {
                existing.count++
                existing.flow
            } else {
                val created = Subscription(
                    message = subscription,
                    flow = MutableSharedFlow(
                        extraBufferCapacity = 128,
                        onBufferOverflow = BufferOverflow.DROP_OLDEST,
                    ),
                    count = 1,
                )
                subscriptions[key] = created
                outbound.trySend(subscribeMessage(subscription).toString())
                if (!started) {
                    started = true
                    connectionJob = scope.launch { runConnection() }
                }
                created.flow
            }
        }
        try {
            shared.collect { emit(it) }
        } finally {
            mutex.withLock {
                val current = subscriptions[key] ?: return@withLock
                current.count--
                if (current.count <= 0) {
                    subscriptions.remove(key)
                    outbound.trySend(unsubscribeMessage(current.message).toString())
                }
            }
        }
    }

    fun close() {
        connectionJob?.cancel()
        connectionJob = null
        scope.cancel()
        outbound.close()
    }

    private suspend fun runConnection() {
        var attempt = 0
        while (scope.isActive) {
            val active = mutex.withLock {
                subscriptions.values.map { subscribeMessage(it.message).toString() }
            }
            try {
                client.webSocket(urlString = wsUrl) {
                    active.forEach { send(Frame.Text(it)) }
                    val pingJob = launch {
                        while (isActive) {
                            delay(PING_INTERVAL_MS)
                            send(Frame.Text(PING_MESSAGE))
                        }
                    }
                    val writerJob = launch {
                        for (text in outbound) send(Frame.Text(text))
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val root = runCatching {
                                json.parseToJsonElement(frame.readText()).asObject()
                            }.getOrNull() ?: continue
                            route(root)
                        }
                    } finally {
                        writerJob.cancel()
                        pingJob.cancel()
                    }
                }
                attempt = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!scope.isActive) return
                println("Hyperliquid websocket disconnected: ${e.message}")
            }
            if (!scope.isActive) return
            attempt++
            val backoff = (1_000L shl (attempt - 1).coerceAtMost(5)).coerceAtMost(30_000L)
            delay(backoff + Random.nextLong(0L, (backoff / 4L).coerceAtLeast(1L)))
        }
    }

    private suspend fun route(root: JsonObject) {
        val channel = root.string("channel") ?: return
        if (channel == "error") {
            _errors.tryEmit(root.toString())
            return
        }
        if (channel == "subscriptionResponse" || channel == "pong") return
        val key = hyperliquidStreamKey(root, channel) ?: return
        val flow = mutex.withLock { subscriptions[key]?.flow } ?: return
        flow.tryEmit(root)
    }

    private fun subscribeMessage(subscription: JsonObject): JsonObject = buildJsonObject {
        put("method", "subscribe")
        put("subscription", subscription)
    }

    private fun unsubscribeMessage(subscription: JsonObject): JsonObject = buildJsonObject {
        put("method", "unsubscribe")
        put("subscription", subscription)
    }

    private companion object {
        const val PING_INTERVAL_MS = 50_000L
        const val PING_MESSAGE = """{"method":"ping"}"""
    }
}
