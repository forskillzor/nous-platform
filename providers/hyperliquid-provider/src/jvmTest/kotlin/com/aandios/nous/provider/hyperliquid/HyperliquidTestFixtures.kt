/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.api.market.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal val testJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun jsonObject(text: String): JsonObject =
    testJson.parseToJsonElement(text).jsonObject

internal fun jsonElement(text: String): JsonElement =
    testJson.parseToJsonElement(text)

internal class FakeHyperliquidInfo(
    private val responses: Map<String, JsonElement>,
) : HyperliquidInfoApi {
    val requests = mutableListOf<JsonObject>()

    override suspend fun info(body: JsonObject): JsonElement {
        requests += body
        val type = body.string("type") ?: error("Fake request is missing type")
        return responses[type] ?: error("No fake response for $type")
    }

    override suspend fun exchange(payload: JsonObject): JsonObject =
        error("Exchange is not used in market-data tests")
}

internal class FakeHyperliquidStreams : HyperliquidStreamApi {
    private val flows = mutableMapOf<String, MutableSharedFlow<JsonObject>>()
    private val subscriptions = mutableListOf<Pair<String, JsonObject>>()
    private val _errors = MutableSharedFlow<String>()
    override val errors: SharedFlow<String> = _errors

    override fun subscribe(key: String, subscription: JsonObject): Flow<JsonObject> {
        subscriptions += key to subscription
        return flows.getOrPut(key) {
            MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
        }
    }

    fun emit(key: String, message: JsonObject) {
        flows.getOrPut(key) {
            MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
        }.tryEmit(message)
    }

    fun subscription(key: String): JsonObject? =
        subscriptions.firstOrNull { it.first == key }?.second
}

internal object HyperliquidFixtures {
    val perpDexs = jsonElement("""[null,{"name":"xyz"},{"name":"flx"}]""")

    val allPerpMetas = jsonElement(
        """
        [
          [
            {
              "universe": [
                {"name":"BTC","szDecimals":5,"maxLeverage":40},
                {"name":"SOL","szDecimals":2,"maxLeverage":20},
                {"name":"SPX","szDecimals":1,"maxLeverage":5},
                {"name":"DELISTED","szDecimals":0,"maxLeverage":3,"isDelisted":true}
              ],
              "collateralToken": 0
            },
            [
              {"markPx":"78065.0","prevDayPx":"77000.0","dayNtlVlm":"1000000.0"},
              {"markPx":"200.0","prevDayPx":"190.0","dayNtlVlm":"50000.0"},
              {"markPx":"7619.7","prevDayPx":"7593.5","dayNtlVlm":"100.0"},
              {"markPx":"1.0","prevDayPx":"1.0","dayNtlVlm":"0.0"}
            ]
          ],
          [
            {
              "universe": [
                {"name":"xyz:XYZ100","szDecimals":4,"maxLeverage":30},
                {"name":"xyz:SP500","szDecimals":3,"maxLeverage":50,"onlyIsolated":true,"marginMode":"noCross"}
              ],
              "collateralToken": 0
            },
            [
              {"markPx":"28647.0","prevDayPx":"28544.0","dayNtlVlm":"168468819.87"},
              {"markPx":"7619.7","prevDayPx":"7593.5","dayNtlVlm":"106713671.74"}
            ]
          ],
          [
            {
              "universe": [
                {"name":"flx:TSLA","szDecimals":3,"maxLeverage":10,"onlyIsolated":true}
              ],
              "collateralToken": 360
            },
            [
              {"markPx":"400.0","prevDayPx":"390.0","dayNtlVlm":"1000.0"}
            ]
          ]
        ]
        """.trimIndent(),
    )

    val spotMeta = jsonElement(
        """
        {"tokens":[
          {"index":0,"name":"USDC"},
          {"index":235,"name":"USDE"},
          {"index":268,"name":"USDT0"},
          {"index":360,"name":"USDH"}
        ]}
        """.trimIndent(),
    )

    fun fakeInfo(extra: Map<String, JsonElement> = emptyMap()): FakeHyperliquidInfo = FakeHyperliquidInfo(
        mapOf(
            "perpDexs" to perpDexs,
            "allPerpMetas" to allPerpMetas,
            "spotMeta" to spotMeta,
        ) + extra,
    )

    fun marketData(
        info: HyperliquidInfoApi = fakeInfo(),
        config: ProviderConfig = ProviderConfig(),
        clock: () -> Long = { 1_000_000_000L },
    ): HyperliquidMarketData = HyperliquidMarketData(info, config, clock)
}
