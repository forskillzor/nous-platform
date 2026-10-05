/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

import io.ktor.http.encodeURLParameter

/**
 * HMAC-SHA256 в hex (lowercase). JVM — javax.crypto.Mac; js/wasmJs — заглушка
 * (торговля только на десктопе).
 */
expect fun hmacSha256Hex(key: String, data: String): String

/**
 * Подпись MEXC Contract API v1.
 *
 * REST:  Signature = HMAC-SHA256(secretKey, accessKey + reqTime + paramString)
 *        paramString: GET — параметры сортированы лексикографически,
 *        URL-encoded, через '&'; POST — сырой JSON-тело.
 * WS:    Signature = HMAC-SHA256(secretKey, accessKey + reqTime)
 */
object MexcSigner {

    /** Подпись REST-запроса. */
    fun signRest(apiKey: String, secretKey: String, reqTime: String, paramString: String): String =
        hmacSha256Hex(secretKey, apiKey + reqTime + paramString)

    /** Подпись WS-login. */
    fun signWs(apiKey: String, secretKey: String, reqTime: String): String =
        hmacSha256Hex(secretKey, apiKey + reqTime)

    /**
     * paramString для GET: непустые параметры сортируются по ключу,
     * значения URL-кодируются, пары через '&'.
     */
    fun getParamString(params: Map<String, String>): String =
        params.filterValues { it.isNotEmpty() }
            .toList()
            .sortedBy { it.first }
            .joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
}
