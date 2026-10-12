/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual fun createBybitHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(CIO, configure)

actual fun bybitCurrentTimeMillis(): Long = System.currentTimeMillis()

actual fun bybitEnvironmentVariable(name: String): String? = System.getenv(name)

actual fun bybitHmacSha256Hex(key: String, data: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key.encodeToByteArray(), "HmacSHA256"))
    return mac.doFinal(data.encodeToByteArray()).joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
