/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui

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
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TradesViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val tradesAdapter = FakeTradesAdapter()
    private val provider = FakeTradesProvider(
        tradesAdapter = tradesAdapter,
        symbolInfoAdapter = FakeSymbolInfoAdapter(),
    )

    private var nextId = 0L

    private fun trade(quantity: Double) = Trade(
        id = nextId++,
        symbol = "BTCUSDT",
        price = 100.0,
        quantity = quantity,
        timestamp = nextId,
        isBuyerMaker = false,
        side = TradeSide.BUY,
    )

    private fun newViewModel() = TradesViewModel(
        providerRegistry = ProviderRegistry(listOf(provider)),
        coroutineDispatcher = testDispatcher,
    )

    @Test
    fun `filtered matches survive feed eviction and reseed from raw`() = testScope.runTest {
        val vm = newViewModel()
        vm.subscribeToTrades("BTCUSDT")
        advanceUntilIdle()

        vm.updateSizeFilter(SizeFilter.Custom(10.0))

        val bigIds = mutableSetOf<Long>()
        repeat(600) { i ->
            val big = i % 100 == 0
            val trade = trade(if (big) 50.0 else 1.0)
            if (big) bigIds += trade.id
            tradesAdapter.trades.emit(trade)
        }
        advanceUntilIdle()

        // ALL-фид ограничен 500, крупная сделка id=0 уже вытеснена из него.
        assertEquals(500, (vm.state.value as TradesState.Connected).trades.size)
        assertTrue(bigIds.first() !in (vm.state.value as TradesState.Connected).trades.map { it.id })

        // Но она осталась в filtered, потому что raw-история больше feed.
        assertEquals(bigIds, vm.filteredBuffer.value.map { it.id }.toSet())

        // Смена фильтра пересобирает filtered из raw, а не из feed.
        vm.updateSizeFilter(SizeFilter.Custom(1000.0))
        assertTrue(vm.filteredBuffer.value.isEmpty())

        vm.updateSizeFilter(SizeFilter.Custom(10.0))
        assertEquals(bigIds, vm.filteredBuffer.value.map { it.id }.toSet())

        vm.updateSizeFilter(SizeFilter.All)
        assertTrue(vm.filteredBuffer.value.isEmpty())
    }

    @Test
    fun `symbol change clears raw and filtered history`() = testScope.runTest {
        val vm = newViewModel()
        vm.subscribeToTrades("BTCUSDT")
        advanceUntilIdle()

        vm.updateSizeFilter(SizeFilter.Custom(10.0))
        tradesAdapter.trades.emit(trade(50.0))
        advanceUntilIdle()
        assertEquals(1, vm.filteredBuffer.value.size)

        vm.subscribeToTrades("ETHUSDT")
        advanceUntilIdle()
        assertTrue(vm.filteredBuffer.value.isEmpty())

        tradesAdapter.trades.emit(trade(1.0))
        advanceUntilIdle()
        assertTrue(vm.filteredBuffer.value.isEmpty())
        assertEquals(1, (vm.state.value as TradesState.Connected).trades.size)
    }
}

private class FakeTradesAdapter : TradesAdapter {
    val trades = MutableSharedFlow<Trade>(extraBufferCapacity = 16_384)

    override fun subscribeToTrades(symbol: String): Flow<Trade> = trades

    override suspend fun getRecentTrades(symbol: String, limit: Int): List<Trade> = emptyList()
}

private class FakeSymbolInfoAdapter : SymbolInfoAdapter {
    private val info = SymbolInfo(
        symbol = "BTCUSDT",
        tickSize = 0.01,
        stepSize = 0.001,
        minQty = 1.0,
        minNotional = 10.0,
        status = "TRADING",
        baseAsset = "BTC",
        quoteAsset = "USDT",
    )

    override suspend fun getSymbolInfo(symbol: String): SymbolInfo = info.copy(symbol = symbol)

    override suspend fun getAllSymbolsInfo(): List<SymbolInfo> = listOf(info)
}

private class FakeTradesProvider(
    tradesAdapter: TradesAdapter,
    symbolInfoAdapter: SymbolInfoAdapter,
    override val providerId: String = "binance-nous-0.0.1",
) : Provider {
    override val providerName = "binance"
    override val version = "0.0.1"
    override val config = ProviderConfig(displayName = "Binance")
    override val networkManager: NetworkManager get() = error("not used")

    override val trades: TradesAdapter = tradesAdapter
    override val dom: DomAdapter? = null
    override val bookTicker: BookTickerAdapter? = null
    override val chart: ChartAdapter? = null
    override val trading: TradingAdapter? = null
    override val symbolInfo: SymbolInfoAdapter = symbolInfoAdapter
    override val liquidation: LiquidationAdapter? = null
}
