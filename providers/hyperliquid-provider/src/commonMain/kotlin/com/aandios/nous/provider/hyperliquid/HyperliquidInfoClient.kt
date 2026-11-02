/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal interface HyperliquidInfoApi {
    suspend fun info(body: JsonObject): JsonElement
    suspend fun exchange(payload: JsonObject): JsonObject
}

internal class HyperliquidInfoClient(
    private val client: HttpClient,
    private val baseUrl: String,
) : HyperliquidInfoApi {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val gate = HyperliquidRestGate()

    override suspend fun info(body: JsonObject): JsonElement = post("/info", body)

    override suspend fun exchange(payload: JsonObject): JsonObject {
        val response = post("/exchange", payload)
        return response as? JsonObject
            ?: error("Hyperliquid exchange response is not an object")
    }

    private suspend fun post(path: String, body: JsonObject): JsonElement {
        gate.await()
        val response = client.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Hyperliquid $path failed (${response.status.value}): ${text.take(500)}")
        }
        return runCatching { json.parseToJsonElement(text) }
            .getOrElse { error("Hyperliquid $path returned invalid JSON: ${text.take(500)}") }
    }
}

internal class HyperliquidRestGate(
    private val minIntervalMs: Long = 40L,
    private val clock: () -> Long = { hyperliquidCurrentTimeMillis() },
) {
    private val mutex = Mutex()
    private var nextRequestAt = 0L

    suspend fun await() {
        mutex.withLock {
            val now = clock()
            val wait = nextRequestAt - now
            if (wait > 0) delay(wait)
            nextRequestAt = clock() + minIntervalMs
        }
    }
}
