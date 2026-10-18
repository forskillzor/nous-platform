/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.market

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.adapters.MarketOverviewAdapter
import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.adapters.TradingAdapter
import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.MarketTickerStats
import com.aandios.nous.api.market.model.SymbolInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MarketOverviewStoreTest {

    private fun info(
        symbol: String,
        base: String,
        quote: String = "USDT",
        contractType: String? = "PERPETUAL",
        marginAsset: String? = "USDT",
        status: String = "TRADING",
    ) = SymbolInfo(
        symbol = symbol,
        tickSize = 0.01,
        stepSize = 0.001,
        minQty = 0.001,
        minNotional = 5.0,
        status = status,
        baseAsset = base,
        quoteAsset = quote,
        contractType = contractType,
        marginAsset = marginAsset,
    )

    private class FakeSymbolInfoAdapter(
        private val infos: List<SymbolInfo>,
        private val fail: Boolean = false,
    ) : SymbolInfoAdapter {
        override suspend fun getSymbolInfo(symbol: String): SymbolInfo? =
            infos.firstOrNull { it.symbol == symbol }

        override suspend fun getAllSymbolsInfo(): List<SymbolInfo> {
            if (fail) error("symbol info unavailable")
            return infos
        }
    }

    /** Как реальные адаптеры провайдеров: BookTickerAdapter + MarketOverviewAdapter. */
    private class FakeOverviewAdapter(
        private val stats: List<MarketTickerStats>,
        private val fail: Boolean = false,
    ) : BookTickerAdapter, MarketOverviewAdapter {
        var calls = 0
            private set

        override suspend fun getMarketOverview(): List<MarketTickerStats> {
            calls++
            if (fail) error("market overview unavailable")
            return stats
        }

        override fun subscribeToBookTicker(symbol: String): Flow<BookTicker> = emptyFlow()

        override suspend fun getBookTickerRest(symbol: String): BookTicker? = null
    }

    private class FakeChartAdapter : ChartAdapter {
        override suspend fun getCandles(symbol: String, interval: String, limit: Int): List<Candle> =
            emptyList()

        override suspend fun getCandlesBefore(symbol: String, interval: String, endTime: Long, limit: Int): List<Candle> =
            emptyList()

        override fun subscribeToCandles(symbol: String, interval: String): Flow<Candle> = emptyFlow()
    }

    private class FakeProvider(
        override val providerId: String,
        private val displayName: String,
        private val symbolInfoAdapter: SymbolInfoAdapter? = null,
        private val overviewAdapter: BookTickerAdapter? = null,
    ) : Provider {
        override val providerName = providerId
        override val version = "0.0.1"
        override val config = ProviderConfig(displayName = displayName)
        override val networkManager: NetworkManager get() = error("not used")
        override val trades: TradesAdapter? = null
        override val dom: DomAdapter? = null
        override val bookTicker: BookTickerAdapter? = overviewAdapter
        override val chart: ChartAdapter? = FakeChartAdapter()
        override val trading: TradingAdapter? = null
        override val symbolInfo: SymbolInfoAdapter? = symbolInfoAdapter
        override val liquidation: LiquidationAdapter? = null
    }

    @Test
    fun `builds rows from symbol infos and market overview`() = runTest {
        val provider = FakeProvider(
            providerId = "binance-nous-0.0.1",
            displayName = "Binance",
            symbolInfoAdapter = FakeSymbolInfoAdapter(
                listOf(
                    info("BTCUSDT", "BTC"),
                    info("BTCUSDC", "BTC", quote = "USDC"),
                    info("BTCUSD", "BTC", quote = "USD", marginAsset = "BTC"),
                    info("ETHUSDT", "ETH", contractType = null, marginAsset = null),
                    info("HALTUSDT", "HALT", status = "HALT"),
                ),
            ),
            overviewAdapter = FakeOverviewAdapter(
                listOf(
                    MarketTickerStats("BTCUSDT", 60_000.0, 1.84, 3.84e9),
                    MarketTickerStats("BTCUSDC", 60_001.0, 0.5, 2.0e8),
                    MarketTickerStats("STATSONLYUSDT", 1.0, -3.0, 1.0e6),
                ),
            ),
        )

        val store = MarketOverviewStore(ProviderRegistry(listOf(provider)))
        store.refresh()

        val rows = store.state.value.rows

        // HALT отфильтрован, STATSONLYUSDT без SymbolInfo тоже в списке
        assertEquals(
            listOf("BTCUSD", "BTCUSDC", "BTCUSDT", "ETHUSDT", "STATSONLYUSDT"),
            rows.map { it.symbol },
        )
        assertNull(store.state.value.error)
        assertFalse(store.state.value.loading)

        val btc = rows.first { it.symbol == "BTCUSDT" }
        assertEquals("binance-nous-0.0.1", btc.exchangeId)
        assertEquals("Binance", btc.exchangeName)
        assertTrue(btc.isPerpetual)
        assertEquals("USDT", btc.quoteAsset)
        assertEquals(0.01, btc.tickSize)
        assertEquals(60_000.0, btc.stats?.lastPrice)
        assertEquals(1.84, btc.stats?.changePercent24h)

        // Inverse (маржа в монете): USD-quote, остаётся perpetual
        val inverse = rows.first { it.symbol == "BTCUSD" }
        assertEquals("USD", inverse.quoteAsset)
        assertTrue(inverse.isPerpetual)

        // Спот: без PERP
        val spot = rows.first { it.symbol == "ETHUSDT" }
        assertFalse(spot.isPerpetual)

        // Только статистика: цены есть, спеки нет
        val statsOnly = rows.first { it.symbol == "STATSONLYUSDT" }
        assertNull(statsOnly.tickSize)
        assertNull(statsOnly.quoteAsset)
        assertEquals(-3.0, statsOnly.stats?.changePercent24h)
    }

    @Test
    fun `broken source keeps the other rows and reports the failure`() = runTest {
        val brokenOverview = FakeProvider(
            providerId = "broken-overview",
            displayName = "BrokenOverview",
            symbolInfoAdapter = FakeSymbolInfoAdapter(listOf(info("BTCUSDT", "BTC"))),
            overviewAdapter = FakeOverviewAdapter(emptyList(), fail = true),
        )
        val brokenInfos = FakeProvider(
            providerId = "broken-infos",
            displayName = "BrokenInfos",
            symbolInfoAdapter = FakeSymbolInfoAdapter(emptyList(), fail = true),
            overviewAdapter = FakeOverviewAdapter(listOf(MarketTickerStats("SOLUSDT", 150.0, 4.2, 5.0e7))),
        )

        val store = MarketOverviewStore(ProviderRegistry(listOf(brokenOverview, brokenInfos)))
        store.refresh()

        // Оба провайдера дали строку: у первого — спека без статистики,
        // у второго — статистика без спеки
        val rows = store.state.value.rows
        val btc = rows.first { it.symbol == "BTCUSDT" }
        assertNull(btc.stats)
        val sol = rows.first { it.symbol == "SOLUSDT" }
        assertEquals(150.0, sol.stats?.lastPrice)

        val error = store.state.value.error.orEmpty()
        assertTrue(error.contains("BrokenOverview"), "error should mention BrokenOverview: $error")
        assertTrue(error.contains("BrokenInfos"), "error should mention BrokenInfos: $error")
    }

    @Test
    fun `start refreshes immediately and repeats on the interval`() = runTest {
        val overview = FakeOverviewAdapter(listOf(MarketTickerStats("BTCUSDT", 60_000.0, 1.0, 1.0e9)))
        val provider = FakeProvider(
            providerId = "binance-nous-0.0.1",
            displayName = "Binance",
            symbolInfoAdapter = FakeSymbolInfoAdapter(listOf(info("BTCUSDT", "BTC"))),
            overviewAdapter = overview,
        )
        val store = MarketOverviewStore(
            registry = ProviderRegistry(listOf(provider)),
            scope = backgroundScope,
            refreshIntervalMs = 1_000L,
        )

        store.start()
        runCurrent()
        assertEquals(1, overview.calls)
        assertEquals(listOf("BTCUSDT"), store.state.value.rows.map { it.symbol })

        // Через интервал — следующий цикл
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(2, overview.calls)

        // Отложенный запуск не дублирует цикл
        store.start()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(3, overview.calls)
    }
}
