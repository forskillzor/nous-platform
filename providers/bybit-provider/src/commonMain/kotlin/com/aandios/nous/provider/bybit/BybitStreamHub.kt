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
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

internal class BybitStreamHub(
    private val client: HttpClient,
    config: ProviderConfig,
) {
    private val endpoint = if (config.isTestnet) {
        "wss://stream-testnet.bybit.com/v5/public/linear"
    } else {
        "wss://stream.bybit.com/v5/public/linear"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val streams = mutableMapOf<String, MutableSharedFlow<String>>()
    private val counts = mutableMapOf<String, Int>()
    private val jobs = mutableMapOf<String, Job>()

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
                    send(Frame.Text("""{"op":"subscribe","args":["$topic"]}"""))
                    val pingJob = launch {
                        while (isActive) {
                            delay(20_000L)
                            send(Frame.Text("""{"op":"ping"}"""))
                        }
                    }
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                mutex.withLock { streams[topic]?.tryEmit(frame.readText()) }
                            }
                        }
                    } finally {
                        pingJob.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Bybit public stream $topic disconnected: ${e.message}")
            }
            attempt++
            val backoff = (1_000L shl (attempt - 1).coerceAtMost(5)).coerceAtMost(30_000L)
            delay(backoff + Random.nextLong(0L, (backoff / 4L).coerceAtLeast(1L)))
        }
    }
}
