/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.bidasker.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    val params = parseUrlParams()
    val config = BidaskerConfig.default().copy(
        symbol = params["symbol"] ?: "BTCUSDT",
        timeframe = params["tf"] ?: "1m"
    )

    CanvasBasedWindow("Bidasker Footprint") {
        BidaskerApp(httpClient = httpClient, config = config)
    }
}

private fun parseUrlParams(): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val search = js("window.location.search") as String
    if (search.isNotEmpty()) {
        val query = search.removePrefix("?")
        for (pair in query.split("&")) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                result[js("decodeURIComponent(parts[0])") as String] =
                    js("decodeURIComponent(parts[1])") as String
            }
        }
    }
    return result
}
