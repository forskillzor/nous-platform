/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import com.aandios.nous.api.market.ProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal class BybitRestClient(
    private val client: HttpClient,
    config: ProviderConfig,
) {
    private val baseUrl = if (config.isTestnet) {
        "https://api-testnet.bybit.com"
    } else {
        "https://api.bybit.com"
    }
    private val rateMutex = Mutex()
    private var nextRequestAt = 0L

    suspend fun get(path: String, params: Map<String, String>): JsonObject {
        rateMutex.withLock {
            val now = bybitCurrentTimeMillis()
            val wait = nextRequestAt - now
            if (wait > 0) delay(wait)
            nextRequestAt = bybitCurrentTimeMillis() + 10L
        }

        val response: JsonObject = client.get("$baseUrl$path") {
            url {
                params.forEach { (key, value) -> parameters.append(key, value) }
            }
        }.body()

        val code = response["retCode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: error("Bybit $path response did not contain retCode")
        if (code != 0) {
            val message = response["retMsg"]?.jsonPrimitive?.contentOrNull ?: "unknown error"
            error("Bybit $path failed (retCode=$code): $message")
        }
        return response["result"] as? JsonObject
            ?: error("Bybit $path response did not contain result")
    }
}
