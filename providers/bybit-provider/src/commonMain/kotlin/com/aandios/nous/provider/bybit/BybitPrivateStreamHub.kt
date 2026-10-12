/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import com.aandios.nous.api.market.ProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

internal class BybitPrivateStreamHub(
    private val client: HttpClient,
    config: ProviderConfig,
    private val apiKey: String,
    private val apiSecret: String,
) {
    private val endpoint = if (config.isTestnet) {
        "wss://stream-testnet.bybit.com/v5/private"
    } else {
        "wss://stream.bybit.com/v5/private"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val streams = mutableMapOf<String, MutableSharedFlow<String>>()
    private val counts = mutableMapOf<String, Int>()
    private val jobs = mutableMapOf<String, Job>()
    private val json = Json { ignoreUnknownKeys = true }

    fun subscribe(topic: String): Flow<String> = flow {
        val shared = mutex.withLock {
            val stream = streams.getOrPut(topic) {
                MutableSharedFlow(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            }
            counts[topic] = (counts[topic] ?: 0) + 1
            if (counts[topic] == 1) jobs[topic] = scope.launch { runTopic(topic) }
            stream
        }
        try {
            shared.collect { emit(it) }
        } finally {
            mutex.withLock {
                val remaining = (counts[topic] ?: 1) - 1
                if (remaining <= 0) {
                    counts.remove(topic)
                    streams.remove(topic)
                    jobs.remove(topic)?.cancel()
                } else {
                    counts[topic] = remaining
                }
            }
        }
    }

    private suspend fun runTopic(topic: String) {
        var attempt = 0
        while (coroutineContext.isActive) {
            if (!mutex.withLock { jobs.containsKey(topic) }) return
            try {
                client.webSocket(urlString = endpoint) {
                    attempt = 0
                    val expires = bybitCurrentTimeMillis() + 10_000L
                    val signature = bybitHmacSha256Hex(apiSecret, "GET/realtime$expires")
                    send(
                        Frame.Text(
                            buildJsonObject {
                                put("op", "auth")
                                put("args", buildJsonArray {
                                    add(kotlinx.serialization.json.JsonPrimitive(apiKey))
                                    add(kotlinx.serialization.json.JsonPrimitive(expires))
                                    add(kotlinx.serialization.json.JsonPrimitive(signature))
                                })
                            }.toString()
                        )
                    )
                    val pingJob = launch {
                        while (isActive) {
                            delay(20_000L)
                            send(Frame.Text("""{"op":"ping"}"""))
                        }
                    }
                    try {
                        var authenticated = false
                        var subscribed = false
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val text = frame.readText()
                            val root = runCatching { json.parseToJsonElement(text).asObject() }.getOrNull()
                            if (root == null) {
                                mutex.withLock { streams[topic]?.tryEmit(text) }
                                continue
                            }
                            when (root.string("op")) {
                                "auth" -> {
                                    if (root["success"]?.toString() != "true") {
                                        error("Bybit private WebSocket authentication failed: ${root["ret_msg"]}")
                                    }
                                    authenticated = true
                                }
                                "subscribe" -> {
                                    if (root["success"]?.toString() != "true") {
                                        error("Bybit private WebSocket subscription failed: ${root["ret_msg"]}")
                                    }
                                    subscribed = true
                                }
                                "pong" -> Unit
                                else -> {
                                    if (authenticated && !subscribed) {
                                        send(Frame.Text("""{"op":"subscribe","args":["$topic"]}"""))
                                        subscribed = true
                                    }
                                    if (root.string("topic") == topic) {
                                        mutex.withLock { streams[topic]?.tryEmit(text) }
                                    }
                                }
                            }
                            if (authenticated && !subscribed) {
                                send(Frame.Text("""{"op":"subscribe","args":["$topic"]}"""))
                                subscribed = true
                            }
                        }
                    } finally {
                        pingJob.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Bybit private stream $topic disconnected: ${e.message}")
            }
            attempt++
            val backoff = (1_000L shl (attempt - 1).coerceAtMost(5)).coerceAtMost(30_000L)
            delay(backoff + Random.nextLong(0L, (backoff / 4L).coerceAtLeast(1L)))
        }
    }
}
