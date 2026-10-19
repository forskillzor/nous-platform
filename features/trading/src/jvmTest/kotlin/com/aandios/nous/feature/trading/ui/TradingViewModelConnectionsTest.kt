/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.ui

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
import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.OrderResponse
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.paper.PaperTrading
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Manage connections: статусы REST API / приватного стрима в
 * [TradingViewModel.connections] — connected / connecting / error.
 */
class TradingViewModelConnectionsTest {

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelWith(adapter: TradingAdapter?): TradingViewModel =
        TradingViewModel(ProviderRegistry(listOf(FakeProvider(adapter = adapter))))

    @Test
    fun `live session marks rest connected and stream error without private subscriptions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModelWith(FakeTradingAdapter(streams = false))
        runCurrent()

        val connections = vm.connections.value
        assertEquals(2, connections.size)
        assertEquals("REST API", connections[0].label)
        assertEquals(TradingConnectionStatus.CONNECTED, connections[0].status)
        assertEquals("Account stream", connections[1].label)
        assertEquals(TradingConnectionStatus.ERROR, connections[1].status)
        assertTrue(connections[1].detail.orEmpty().isNotBlank())
        vm.dispose()
    }

    @Test
    fun `live session marks stream connected when private subscriptions exist`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModelWith(FakeTradingAdapter(streams = true))
        runCurrent()

        val connections = vm.connections.value
        assertEquals(TradingConnectionStatus.CONNECTED, connections[0].status)
        assertEquals(TradingConnectionStatus.CONNECTED, connections[1].status)
        vm.dispose()
    }

    @Test
    fun `rest failure surfaces as error with detail`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModelWith(FakeTradingAdapter(streams = true, balanceError = "API keys are not configured"))
        runCurrent()

        val rest = vm.connections.value[0]
        assertEquals(TradingConnectionStatus.ERROR, rest.status)
        assertEquals("API keys are not configured", rest.detail)
        vm.dispose()
    }

    @Test
    fun `missing trading adapter marks both connections error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModelWith(null)
        runCurrent()

        val connections = vm.connections.value
        assertEquals(TradingConnectionStatus.ERROR, connections[0].status)
        assertEquals(TradingConnectionStatus.ERROR, connections[1].status)
        assertNotNull(connections[0].detail)
        vm.dispose()
    }

    @Test
    fun `paper mode reports connected locally without api keys`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        PaperTrading.adapter.reset()
        val vm = viewModelWith(FakeTradingAdapter(streams = false))
        runCurrent()
        vm.setPaperEnabled(true)
        runCurrent()

        val connections = vm.connections.value
        assertEquals(TradingConnectionStatus.CONNECTED, connections[0].status)
        assertEquals(TradingConnectionStatus.CONNECTED, connections[1].status)
        assertEquals("Paper engine", connections[1].detail)
        vm.dispose()
    }
}

private class FakeTradingAdapter(
    private val streams: Boolean,
    private val balanceError: String? = null,
) : TradingAdapter {
    override suspend fun placeOrder(request: OrderRequest): OrderResponse = error("not used")
    override suspend fun cancelOrder(orderId: String): Boolean = false
    override suspend fun getBalances(): List<Balance> =
        balanceError?.let { error(it) } ?: emptyList()
    override suspend fun getPositions(): List<Position> = emptyList()
    override fun subscribeToPositions(): Flow<Position>? = if (streams) flowOf() else null
    override fun subscribeToOrders(): Flow<Order>? = if (streams) flowOf() else null
    override fun subscribeToBalances(): Flow<Balance>? = if (streams) flowOf() else null
}

private class FakeProvider(
    private val adapter: TradingAdapter?,
) : Provider {
    override val providerId: String = "testex"
    override val providerName: String = "TestEx"
    override val version: String = "test"
    override val config: ProviderConfig = ProviderConfig(displayName = "TestEx")
    override val networkManager: NetworkManager = object : NetworkManager {
        override val httpClient: HttpClient = HttpClient(MockEngine { respond("") })
    }
    override val trades: TradesAdapter? = null
    override val dom: DomAdapter? = null
    override val bookTicker: BookTickerAdapter? = null
    override val chart: ChartAdapter? = null
    override val trading: TradingAdapter? = adapter
    override val symbolInfo: SymbolInfoAdapter? = null
    override val liquidation: LiquidationAdapter? = null
}
