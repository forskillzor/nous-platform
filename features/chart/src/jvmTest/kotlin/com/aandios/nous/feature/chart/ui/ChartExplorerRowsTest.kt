/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

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
import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.api.market.model.MarketTickerStats
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.ui.component.SymbolExplorerRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChartExplorerRowsTest {

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

        val rows = buildExplorerRows(ProviderRegistry(listOf(provider)))

        // HALT отфильтрован, STATSONLYUSDT без SymbolInfo тоже в списке
        assertEquals(
            listOf("BTCUSD", "BTCUSDC", "BTCUSDT", "ETHUSDT", "STATSONLYUSDT"),
            rows.map { it.symbol },
        )

        val btc = rows.first { it.symbol == "BTCUSDT" }
        assertEquals("BTCUSDT.P", btc.displaySymbol)
        assertTrue(btc.isPerpetual)
        assertEquals("BINANCE", btc.exchangeLabel)
        assertEquals("binance-nous-0.0.1", btc.exchangeId)
        assertEquals("USDT", btc.quoteAsset)
        assertEquals(0.01, btc.tickSize)
        assertEquals(60_000.0, btc.lastPrice)
        assertEquals(1.84, btc.changePercent24h)
        assertEquals(3.84e9, btc.quoteVolume24h)

        // Inverse (маржа в монете): USD-quote, остаётся perpetual
        val inverse = rows.first { it.symbol == "BTCUSD" }
        assertEquals("USD", inverse.quoteAsset)
        assertTrue(inverse.isPerpetual)

        // Спот: без суффикса .P и без PERP
        val spot = rows.first { it.symbol == "ETHUSDT" }
        assertEquals("ETHUSDT", spot.displaySymbol)
        assertFalse(spot.isPerpetual)

        // Только статистика: цены есть, спеки нет
        val statsOnly = rows.first { it.symbol == "STATSONLYUSDT" }
        assertNull(statsOnly.tickSize)
        assertNull(statsOnly.quoteAsset)
        assertEquals(-3.0, statsOnly.changePercent24h)
    }

    @Test
    fun `broken overview keeps symbol infos and broken provider is skipped`() = runTest {
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

        val rows = buildExplorerRows(ProviderRegistry(listOf(brokenOverview, brokenInfos)))

        // Оба провайдера дали строку: у первого — спека без статистики,
        // у второго — статистика без спеки
        val btc = rows.first { it.symbol == "BTCUSDT" }
        assertNull(btc.lastPrice)
        val sol = rows.first { it.symbol == "SOLUSDT" }
        assertEquals(150.0, sol.lastPrice)
    }

    // ── ChartViewModel: выбор из explorer ──

    private fun createViewModel(vararg providers: Provider) = ChartViewModel(
        providerRegistry = ProviderRegistry(providers.toList()),
    )

    @Test
    fun `selecting a row on the same exchange syncs contract type and symbol`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val provider = FakeProvider(
                providerId = "binance-nous-0.0.1",
                displayName = "Binance",
                symbolInfoAdapter = FakeSymbolInfoAdapter(
                    listOf(
                        info("BTCUSDT", "BTC"),
                        info("ETHUSD", "ETH", quote = "USD", marginAsset = "BTC"),
                    ),
                ),
            )
            val vm = createViewModel(provider)
            advanceUntilIdle()
            assertEquals("BTCUSDT", vm.state.value.currentSymbol)

            vm.selectExplorerRow(
                SymbolExplorerRow(
                    symbol = "ETHUSD",
                    displaySymbol = "ETHUSD.P",
                    exchangeId = "binance-nous-0.0.1",
                    exchangeLabel = "BINANCE",
                )
            )
            advanceUntilIdle()

            assertEquals("ETHUSD", vm.state.value.currentSymbol)
            assertEquals(ContractType.COIN_M, vm.state.value.contractType)
            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `selecting a row on another exchange switches provider and disables trading`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val binance = FakeProvider(
                providerId = "binance-nous-0.0.1",
                displayName = "Binance",
                symbolInfoAdapter = FakeSymbolInfoAdapter(listOf(info("BTCUSDT", "BTC"))),
            )
            val bybit = FakeProvider(
                providerId = "bybit-nous-0.0.1",
                displayName = "Bybit",
                symbolInfoAdapter = FakeSymbolInfoAdapter(listOf(info("SOLUSDT", "SOL"))),
            )
            val vm = createViewModel(binance, bybit)
            advanceUntilIdle()
            vm.setTradingEnabled(true)
            advanceUntilIdle()
            assertTrue(vm.tradingEnabled.value)

            vm.selectExplorerRow(
                SymbolExplorerRow(
                    symbol = "SOLUSDT",
                    displaySymbol = "SOLUSDT.P",
                    exchangeId = "bybit-nous-0.0.1",
                    exchangeLabel = "BYBIT",
                )
            )
            advanceUntilIdle()

            assertEquals("bybit-nous-0.0.1", vm.state.value.currentProviderId)
            assertEquals("SOLUSDT", vm.state.value.currentSymbol)
            // Смена биржи — Trading OFF (инвариант безопасности)
            assertFalse(vm.tradingEnabled.value)
            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `refresh loads explorer rows and respects throttle until forced`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val overview = FakeOverviewAdapter(listOf(MarketTickerStats("BTCUSDT", 60_000.0, 1.0, 1.0e9)))
            val provider = FakeProvider(
                providerId = "binance-nous-0.0.1",
                displayName = "Binance",
                symbolInfoAdapter = FakeSymbolInfoAdapter(listOf(info("BTCUSDT", "BTC"))),
                overviewAdapter = overview,
            )
            val vm = createViewModel(provider)
            advanceUntilIdle()

            vm.refreshExplorerRows()
            advanceUntilIdle()
            assertEquals(1, overview.calls)
            assertEquals(listOf("BTCUSDT"), vm.explorerRows.value.map { it.symbol })

            // Повторный вызов без force — троттлится
            vm.refreshExplorerRows()
            advanceUntilIdle()
            assertEquals(1, overview.calls)

            vm.refreshExplorerRows(force = true)
            advanceUntilIdle()
            assertEquals(2, overview.calls)
            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
