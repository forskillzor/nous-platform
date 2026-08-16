/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.network

import com.aandios.nous.api.market.NetworkManager
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

class NetworkManagerImpl : NetworkManager {
    override val httpClient: HttpClient by lazy {
        createPlatformHttpClient {
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
                })
            }

            // Add default request headers
            expectSuccess = false
        }
    }
}