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
import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.adapters.TradingAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.ContractType
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
    ) : SymbolInfoAdapter {
        override suspend fun getSymbolInfo(symbol: String): SymbolInfo? =
            infos.firstOrNull { it.symbol == symbol }

        override suspend fun getAllSymbolsInfo(): List<SymbolInfo> = infos
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
    ) : Provider {
        override val providerName = providerId
        override val version = "0.0.1"
        override val config = ProviderConfig(displayName = displayName)
        override val networkManager: NetworkManager get() = error("not used")
        override val trades: TradesAdapter? = null
        override val dom: DomAdapter? = null
        override val bookTicker: BookTickerAdapter? = null
        override val chart: ChartAdapter? = FakeChartAdapter()
        override val trading: TradingAdapter? = null
        override val symbolInfo: SymbolInfoAdapter? = symbolInfoAdapter
        override val liquidation: LiquidationAdapter? = null
    }

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
    fun `explorer rows come from the shared market overview store`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val provider = FakeProvider(
                providerId = "binance-nous-0.0.1",
                displayName = "Binance",
                symbolInfoAdapter = FakeSymbolInfoAdapter(listOf(info("BTCUSDT", "BTC"))),
            )
            val store = com.aandios.nous.core.market.MarketOverviewStore(
                registry = ProviderRegistry(listOf(provider)),
            )
            val vm = ChartViewModel(
                providerRegistry = ProviderRegistry(listOf(provider)),
                marketOverviewStore = store,
            )
            advanceUntilIdle()
            assertEquals(emptyList(), vm.explorerRows.value)

            store.refresh()
            advanceUntilIdle()
            assertEquals(listOf("BTCUSDT"), vm.explorerRows.value.map { it.symbol })
            assertEquals("BINANCE", vm.explorerRows.value.single().exchangeLabel)
            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
