/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HyperliquidInfoClientTest {
    @Test
    fun `posts the raw json body and parses array responses`() = runTest {
        var bodyText = ""
        val engine = MockEngine { request ->
            bodyText = (request.body as? TextContent)?.text.orEmpty()
            respond(
                content = """[{"type":"ok"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val info = HyperliquidInfoClient(client, "https://api.hyperliquid.xyz")
            val response = info.info(buildJsonObject { put("type", "allMids") })

            assertEquals("""{"type":"allMids"}""", bodyText)
            assertEquals(1, response.asArray()?.size)
        } finally {
            client.close()
        }
    }

    @Test
    fun `propagates http errors with the response body`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"bad request"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        try {
            val info = HyperliquidInfoClient(client, "https://api.hyperliquid.xyz")
            val failure = runCatching { info.info(buildJsonObject { put("type", "allMids") }) }
            assertTrue(failure.isFailure)
            assertTrue(failure.exceptionOrNull()?.message?.contains("400") == true)
        } finally {
            client.close()
        }
    }
}
