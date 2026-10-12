/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import com.aandios.nous.api.market.ProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal class BybitTradingClient(
    private val client: HttpClient,
    config: ProviderConfig,
    val apiKey: String,
    private val apiSecret: String,
) {
    private val baseUrl = if (config.isTestnet) {
        "https://api-testnet.bybit.com"
    } else {
        "https://api.bybit.com"
    }
    private val json = Json { ignoreUnknownKeys = true }
    val hasCredentials: Boolean get() = apiKey.isNotBlank() && apiSecret.isNotBlank()

    suspend fun signedGet(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        requireCredentials()
        val query = params.toList().sortedBy { it.first }
            .joinToString("&") { (key, value) -> "${key.encodeURLParameter()}=${value.encodeURLParameter()}" }
        val timestamp = bybitCurrentTimeMillis().toString()
        val recvWindow = RECV_WINDOW
        val signature = bybitHmacSha256Hex(apiSecret, timestamp + apiKey + recvWindow + query)
        val url = if (query.isEmpty()) "$baseUrl$path" else "$baseUrl$path?$query"
        return checkedResponse(
            client.get(url) {
                header("X-BAPI-API-KEY", apiKey)
                header("X-BAPI-TIMESTAMP", timestamp)
                header("X-BAPI-RECV-WINDOW", recvWindow)
                header("X-BAPI-SIGN", signature)
            }.body(),
            path,
        )
    }

    suspend fun signedPost(path: String, body: JsonObject): JsonObject {
        requireCredentials()
        val bodyText = body.toString()
        val timestamp = bybitCurrentTimeMillis().toString()
        val recvWindow = RECV_WINDOW
        val signature = bybitHmacSha256Hex(apiSecret, timestamp + apiKey + recvWindow + bodyText)
        return checkedResponse(
            client.post("$baseUrl$path") {
                contentType(ContentType.Application.Json)
                setBody(bodyText)
                header("X-BAPI-API-KEY", apiKey)
                header("X-BAPI-TIMESTAMP", timestamp)
                header("X-BAPI-RECV-WINDOW", recvWindow)
                header("X-BAPI-SIGN", signature)
            }.body(),
            path,
        )
    }

    private fun requireCredentials() {
        check(hasCredentials) { "Bybit API keys not set (BYBIT_API_KEY / BYBIT_SECRET_KEY)" }
    }

    private fun checkedResponse(response: JsonObject, path: String): JsonObject {
        val retCode = response["retCode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: error("Bybit $path response did not contain retCode")
        if (retCode != 0) {
            val retMsg = response["retMsg"]?.jsonPrimitive?.contentOrNull ?: "unknown error"
            error("Bybit $path failed (retCode=$retCode): $retMsg")
        }
        return response["result"] as? JsonObject ?: JsonObject(emptyMap())
    }

    private companion object {
        const val RECV_WINDOW = "5000"
    }
}
