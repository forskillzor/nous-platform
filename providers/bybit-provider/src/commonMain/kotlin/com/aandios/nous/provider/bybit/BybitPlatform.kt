/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

expect fun createBybitHttpClient(configure: io.ktor.client.HttpClientConfig<*>.() -> Unit): io.ktor.client.HttpClient

expect fun bybitCurrentTimeMillis(): Long

expect fun bybitEnvironmentVariable(name: String): String?

expect fun bybitHmacSha256Hex(key: String, data: String): String
