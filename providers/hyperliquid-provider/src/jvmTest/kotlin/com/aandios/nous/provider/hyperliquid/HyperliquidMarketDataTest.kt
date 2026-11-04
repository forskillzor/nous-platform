/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.model.ContractType
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HyperliquidMarketDataTest {
    @Test
    fun `maps default and hip3 assets with runtime asset ids`() = runTest {
        val info = HyperliquidFixtures.fakeInfo()
        val marketData = HyperliquidFixtures.marketData(info)

        val symbols = marketData.symbols()
        val btc = symbols.first { it.symbol == "BTC" }
        val sp500 = symbols.first { it.symbol == "xyz:SP500" }
        val tsla = symbols.first { it.symbol == "flx:TSLA" }

        assertEquals("BTC", btc.baseAsset)
        assertEquals("USDC", btc.quoteAsset)
        assertEquals("PERPETUAL", btc.contractType)
        assertEquals(0.00001, btc.stepSize, 1e-12)
        assertEquals(10.0, btc.minNotional, 1e-12)

        assertEquals("SP500", sp500.baseAsset)
        assertEquals("USDC", sp500.quoteAsset)
        assertEquals(0.001, sp500.stepSize, 1e-12)
        assertEquals(0.001, sp500.minQty, 1e-12)
        assertEquals(0.1, sp500.tickSize, 1e-12)
        assertEquals("TRADING", sp500.status)

        assertEquals("USDH", tsla.quoteAsset)
        assertFalse(tsla.isInverse)
        assertEquals(ContractType.USDT_M, ContractType.of(tsla))

        assertEquals(
            110_001,
            marketData.assetFor("xyz:SP500")?.assetId,
        )
        assertEquals(
            120_000,
            marketData.assetFor("FLX:TSLA")?.assetId,
        )
        assertEquals(2, marketData.assetFor("SPX")?.assetId)
        assertEquals(110_052, hyperliquidAssetId(dexIndex = 1, indexInMeta = 52))
    }

    @Test
    fun `filters delisted assets from the catalog and market overview`() = runTest {
        val marketData = HyperliquidFixtures.marketData(HyperliquidFixtures.fakeInfo())

        assertTrue(marketData.symbols().none { it.symbol == "DELISTED" })
        assertTrue(marketData.marketOverview().none { it.symbol == "DELISTED" })
        assertEquals("DELISTED", marketData.assetFor("DELISTED")?.let {
            marketData.symbolInfoFor(it).status
        })
    }

    @Test
    fun `maps 24h statistics from asset contexts`() = runTest {
        val marketData = HyperliquidFixtures.marketData(HyperliquidFixtures.fakeInfo())
        val sp500 = marketData.marketOverview().first { it.symbol == "xyz:SP500" }

        assertEquals(7619.7, sp500.lastPrice, 1e-12)
        assertEquals(106_713_671.74, sp500.quoteVolume24h, 1e-6)
        val expected = (7619.7 - 7593.5) / 7593.5 * 100.0
        assertTrue(abs(sp500.changePercent24h - expected) < 1e-9)
    }

    @Test
    fun `resolves bybit style quote suffixes`() = runTest {
        val marketData = HyperliquidFixtures.marketData(HyperliquidFixtures.fakeInfo())

        assertEquals("SOL", marketData.assetFor("SOLUSDT")?.coin)
        assertEquals(1, marketData.assetFor("SOL")?.assetId)
        assertEquals("BTC", marketData.assetFor("btcusdc")?.coin)
        assertNull(marketData.assetFor("NOT_A_COIN"))
    }

    @Test
    fun `caches the metadata snapshot`() = runTest {
        val info = HyperliquidFixtures.fakeInfo()
        val marketData = HyperliquidFixtures.marketData(info)

        marketData.symbols()
        assertEquals(3, info.requests.size)
        marketData.symbols()
        marketData.marketOverview()
        assertEquals(3, info.requests.size)
        assertTrue(info.requests.mapNotNull { it.string("type") }.containsAll(
            listOf("perpDexs", "allPerpMetas", "spotMeta"),
        ))
    }

    @Test
    fun `parses the flat live allPerpMetas response and fetches per-dex contexts`() = runTest {
        var contextCalls = 0
        val info = FakeHyperliquidInfo(handler = { body ->
            when (body.string("type")) {
                "perpDexs" -> jsonElement("""[null,{"name":"xyz"}]""")
                "allPerpMetas" -> jsonElement(
                    """
                    [
                      {"universe":[
                        {"name":"BTC","szDecimals":5,"maxLeverage":40},
                        {"name":"SOL","szDecimals":2,"maxLeverage":20}
                      ],"collateralToken":0},
                      {"universe":[
                        {"name":"xyz:XYZ100","szDecimals":4,"maxLeverage":30},
                        {"name":"xyz:SP500","szDecimals":3,"maxLeverage":50,"onlyIsolated":true,"marginMode":"noCross"}
                      ],"collateralToken":0}
                    ]
                    """.trimIndent(),
                )
                "spotMeta" -> HyperliquidFixtures.spotMeta
                "metaAndAssetCtxs" -> {
                    contextCalls++
                    when (body.string("dex")) {
                        null, "" -> jsonElement(
                            """
                            [{"universe":[{"name":"BTC","szDecimals":5},{"name":"SOL","szDecimals":2}],"collateralToken":0},
                             [{"markPx":"78065.0","prevDayPx":"77000.0","dayNtlVlm":"1000000.0"},
                              {"markPx":"200.0","prevDayPx":"190.0","dayNtlVlm":"50000.0"}]]
                            """.trimIndent(),
                        )
                        "xyz" -> jsonElement(
                            """
                            [{"universe":[{"name":"xyz:XYZ100","szDecimals":4},{"name":"xyz:SP500","szDecimals":3}],"collateralToken":0},
                             [{"markPx":"28647.0","prevDayPx":"28544.0","dayNtlVlm":"168468819.87"},
                              {"markPx":"7619.7","prevDayPx":"7593.5","dayNtlVlm":"106713671.74"}]]
                            """.trimIndent(),
                        )
                        else -> error("Unexpected dex ${body.string("dex")}")
                    }
                }
                else -> error("Unexpected info ${body.string("type")}")
            }
        })
        val marketData = HyperliquidMarketData(info, ProviderConfig(), clock = { 1_000_000_000L })

        val direct = marketData.symbolInfo("xyz:SP500")
        assertNotNull(direct)
        assertEquals(0.1, direct.tickSize, 1e-12)
        assertEquals(1, contextCalls)

        val symbols = marketData.symbols()
        assertEquals(4, symbols.size)
        val btc = symbols.first { it.symbol == "BTC" }
        val sp500 = symbols.first { it.symbol == "xyz:SP500" }
        assertEquals(1.0, btc.tickSize, 1e-12)
        assertEquals("USDC", sp500.quoteAsset)
        assertEquals(0.1, sp500.tickSize, 1e-12)
        assertEquals(110_001, marketData.assetFor("xyz:SP500")?.assetId)
        assertEquals(2, contextCalls)

        val overview = marketData.marketOverview().first { it.symbol == "xyz:SP500" }
        assertEquals(7619.7, overview.lastPrice, 1e-12)
        assertEquals(106_713_671.74, overview.quoteVolume24h, 1e-6)
        assertEquals(2, contextCalls)
        assertEquals(78_065.0, marketData.markPrices(listOf(""))["BTC"])
    }

    @Test
    fun `flat allPerpMetas still yields symbols when contexts are unavailable`() = runTest {
        val info = FakeHyperliquidInfo(handler = { body ->
            when (body.string("type")) {
                "perpDexs" -> jsonElement("""[null,{"name":"xyz"}]""")
                "allPerpMetas" -> jsonElement(
                    """
                    [
                      {"universe":[{"name":"BTC","szDecimals":5,"maxLeverage":40}],"collateralToken":0},
                      {"universe":[{"name":"xyz:SP500","szDecimals":3,"maxLeverage":50}],"collateralToken":0}
                    ]
                    """.trimIndent(),
                )
                "spotMeta" -> HyperliquidFixtures.spotMeta
                "metaAndAssetCtxs" -> error("contexts unavailable")
                else -> error("Unexpected info ${body.string("type")}")
            }
        })
        val marketData = HyperliquidMarketData(info, ProviderConfig(), clock = { 1_000_000_000L })

        val symbols = marketData.symbols()
        assertEquals(2, symbols.size)
        assertTrue(marketData.marketOverview().isEmpty())
    }

    @Test
    fun `honours the dex allow list while keeping the default dex`() = runTest {
        val marketData = HyperliquidFixtures.marketData(
            info = HyperliquidFixtures.fakeInfo(),
            config = ProviderConfig(customSettings = mapOf("dexes" to "xyz")),
        )
        val symbols = marketData.symbols()

        assertNotNull(symbols.firstOrNull { it.symbol == "BTC" })
        assertNotNull(symbols.firstOrNull { it.symbol == "xyz:SP500" })
        assertNull(symbols.firstOrNull { it.symbol == "flx:TSLA" })
    }
}
