/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.footprint

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FootprintApiClientTest {

    @Test
    fun `batch request posts mixed timeframes and decodes grouped response`() = runBlocking {
        var capturedBody = ""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/footprint/batch", request.url.encodedPath)
            capturedBody = (request.body as? TextContent)?.text.orEmpty()
            respond(
                content = """{
                    "exchange":"Binance",
                    "symbol":"BTCUSDT",
                    "results":[
                        {"timeframe":"15m","candles":[
                            {"exchange":"Binance","symbol":"BTCUSDT","timeframe":"15m",
                             "startTime":0,"endTime":900000,"totalTicks":3,
                             "minPrice":"100","maxPrice":"101","levels":[
                                {"price":"100","bidVolume":"1","askVolume":"2","bidCount":1,"askCount":1}
                             ]}
                        ]},
                        {"timeframe":"1m","candles":[]}
                    ]
                }""".trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        try {
            val api = FootprintApiClient(client, "http://test")
            val response = api.getBatchFootprint(
                symbol = "BTCUSDT",
                queries = listOf(
                    BatchFootprintQuery(
                        timeframe = "15m",
                        start = 0L,
                        end = 900_000L,
                        limit = 4,
                        order = "asc",
                    ),
                    BatchFootprintQuery(
                        timeframe = "1m",
                        start = 0L,
                        end = 900_000L,
                        limit = 15,
                        order = "asc",
                    ),
                ),
            )

            assertEquals("Binance", response.exchange)
            assertEquals("BTCUSDT", response.symbol)
            assertEquals(listOf("15m", "1m"), response.results.map { it.timeframe })
            assertEquals(0L, response.results.first().candles.single().startTime)
            assertEquals(3L, response.results.first().candles.single().totalTicks)

            val body = Json.parseToJsonElement(capturedBody).jsonObject
            assertEquals("Binance", body["exchange"]?.jsonPrimitive?.content)
            assertEquals("BTCUSDT", body["symbol"]?.jsonPrimitive?.content)
            val queries = body["queries"]?.jsonArray.orEmpty()
            assertEquals(2, queries.size)
            assertEquals("15m", queries[0].jsonObject["timeframe"]?.jsonPrimitive?.content)
            assertEquals(0L, queries[0].jsonObject["start"]?.jsonPrimitive?.content?.toLong())
            assertEquals("asc", queries[0].jsonObject["order"]?.jsonPrimitive?.content)
            assertEquals("1m", queries[1].jsonObject["timeframe"]?.jsonPrimitive?.content)
            assertEquals(15, queries[1].jsonObject["limit"]?.jsonPrimitive?.content?.toInt())
        } finally {
            client.close()
        }
    }

    @Test
    fun `batch non-2xx throws instead of decoding an empty response`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"error":"invalid request body"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val api = FootprintApiClient(client, "http://test")
            val error = assertFailsWith<IllegalStateException> {
                api.getBatchFootprint(
                    symbol = "BTCUSDT",
                    queries = listOf(BatchFootprintQuery(timeframe = "1m")),
                )
            }
            assertTrue(error.message?.contains("HTTP 400") == true, error.message.orEmpty())
        } finally {
            client.close()
        }
    }
}
