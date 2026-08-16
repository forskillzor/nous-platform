/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

object BinanceHttpClientFactory {
    fun create(): HttpClient = createBinanceHttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 30000
        }

        install(WebSockets) {
            pingInterval = 30000.milliseconds
            maxFrameSize = Long.MAX_VALUE
        }

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
                classDiscriminator = "filterType"
            })
        }

        expectSuccess = false
    }
}

expect fun createBinanceHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient