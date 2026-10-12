/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

actual fun createBybitHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(configure)

actual fun bybitCurrentTimeMillis(): Long = kotlin.js.Date.now().toLong()

actual fun bybitEnvironmentVariable(name: String): String? = null

actual fun bybitHmacSha256Hex(key: String, data: String): String = ""
