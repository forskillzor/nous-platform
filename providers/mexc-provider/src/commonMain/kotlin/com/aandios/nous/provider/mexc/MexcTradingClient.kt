/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

import com.aandios.nous.api.market.ProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Подписанный REST-клиент MEXC Contract API (private endpoints).
 *
 * Заголовки: ApiKey, Request-Time (ms), Signature =
 * HMAC-SHA256(secret, accessKey + reqTime + paramString) hex lowercase.
 * GET — параметры сортированы и URL-encoded; POST — сырой JSON-тело.
 *
 * Ключи берутся из ProviderConfig или переменных окружения
 * MEXC_API_KEY / MEXC_SECRET_KEY (безопаснее — из env).
 */
class MexcTradingClient(
    private val client: HttpClient,
    private val config: ProviderConfig,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val apiKey: String get() = config.apiKey ?: getEnv("MEXC_API_KEY").orEmpty()
    val secretKey: String get() = config.secretKey ?: getEnv("MEXC_SECRET_KEY").orEmpty()

    val hasCredentials: Boolean get() = apiKey.isNotEmpty() && secretKey.isNotEmpty()

    suspend fun signedGet(path: String, params: Map<String, String> = emptyMap()): JsonElement {
        val reqTime = currentTimeMillis().toString()
        val paramString = MexcSigner.getParamString(params)
        val signature = MexcSigner.signRest(apiKey, secretKey, reqTime, paramString)
        return client.get("$MEXC_BASE_URL/$path") {
            url {
                params.forEach { (k, v) -> parameters.append(k, v) }
            }
            header("ApiKey", apiKey)
            header("Request-Time", reqTime)
            header("Signature", signature)
        }.body<JsonElement>()
    }

    suspend fun signedPost(path: String, body: String): JsonElement {
        val reqTime = currentTimeMillis().toString()
        val signature = MexcSigner.signRest(apiKey, secretKey, reqTime, body)
        return client.post("$MEXC_BASE_URL/$path") {
            contentType(ContentType.Application.Json)
            setBody(body)
            header("ApiKey", apiKey)
            header("Request-Time", reqTime)
            header("Signature", signature)
        }.body<JsonElement>()
    }

    /** Форматирует Double в plain-десятичную строку (без экспоненты). */
    fun decimal(value: Double): String = formatDecimal(value)
}
