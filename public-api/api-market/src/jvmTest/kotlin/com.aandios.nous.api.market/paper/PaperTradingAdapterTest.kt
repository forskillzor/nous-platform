/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.paper

import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaperTradingAdapterTest {

    private suspend fun usdtBalance(adapter: PaperTradingAdapter): Double =
        adapter.portfolio().balances.firstOrNull { it.currency == "USDT" }?.amount?.toDoubleOrNull() ?: 0.0

    @Test
    fun `topUp increases balance`() = runTest {
        val adapter = PaperTradingAdapter()
        assertEquals(10000.0, usdtBalance(adapter))
        adapter.topUp("USDT", 500.0)
        assertEquals(10500.0, usdtBalance(adapter))
        adapter.topUp("BTC", 0.5)
        assertEquals(0.5, adapter.portfolio().balances.first { it.currency == "BTC" }.amount.toDouble())
    }

    @Test
    fun `market buy opens long position and reduces available`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        val response = adapter.placeOrder(
            OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, quantity = 1.0)
        )
        assertTrue(response.success)

        val p = adapter.portfolio()
        assertEquals(1, p.positions.size)
        assertEquals(TradeSide.BUY, p.positions[0].side)
        assertEquals(100.0, p.positions[0].avgPrice)
        assertEquals(9900.0, usdtBalance(adapter)) // 10000 - 1*100
    }

    @Test
    fun `market sell close realizes pnl`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, quantity = 1.0))
        adapter.setMarkPrice("BTCUSDT", 150.0)

        val close = adapter.closePosition("BTCUSDT")
        assertTrue(close?.success == true)

        val p = adapter.portfolio()
        assertTrue(p.positions.isEmpty(), "позиция должна закрыться")
        assertEquals(10050.0, usdtBalance(adapter)) // 9900 + 100 (маржа) + 50 (pnl)
        assertEquals(50.0, p.history.last().pnl)
    }

    @Test
    fun `marketable limit order fills immediately`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        // buy limit выше mark — рыночная (сразу исполняется)
        val response = adapter.placeOrder(
            OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, quantity = 1.0, price = 101.0)
        )
        assertTrue(response.success)
        assertEquals(1, adapter.portfolio().positions.size)
    }

    @Test
    fun `limit order above mark stays open and fills on repricing`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        val response = adapter.placeOrder(
            OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, quantity = 1.0, price = 95.0)
        )
        assertTrue(response.success)
        assertEquals(1, adapter.portfolio().openOrders.size)
        assertEquals(OrderStatus.OPEN, adapter.portfolio().openOrders[0].status)

        adapter.setMarkPrice("BTCUSDT", 94.0) // цена пересекла лимитку
        assertEquals(0, adapter.portfolio().openOrders.size)
        assertEquals(1, adapter.portfolio().positions.size)
        assertEquals(95.0, adapter.portfolio().positions[0].avgPrice)
    }

    @Test
    fun `cancel order removes open order`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        val response = adapter.placeOrder(
            OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, quantity = 1.0, price = 95.0)
        )
        assertTrue(adapter.cancelOrder(response.orderId))
        assertEquals(0, adapter.portfolio().openOrders.size)
    }

    @Test
    fun `cancel all orders`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, 1.0, price = 95.0))
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.SELL, OrderType.LIMIT, 1.0, price = 120.0))
        assertTrue(adapter.cancelAllOrders("BTCUSDT"))
        assertEquals(0, adapter.portfolio().openOrders.size)
    }

    @Test
    fun `short position pnl negative on price rise`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.SELL, OrderType.MARKET, quantity = 1.0))
        val p = adapter.portfolio()
        assertEquals(TradeSide.SELL, p.positions[0].side)

        adapter.setMarkPrice("BTCUSDT", 120.0)
        assertEquals(-20.0, adapter.portfolio().positions[0].unrealizedPnl)

        adapter.closePosition("BTCUSDT")
        assertEquals(-20.0, adapter.portfolio().history.last().pnl)
    }

    @Test
    fun `setPositionMode rejects when positions open`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, quantity = 1.0))
        assertFalse(adapter.setPositionMode(1))
        adapter.closePosition("BTCUSDT")
        assertTrue(adapter.setPositionMode(1))
        assertEquals(1, adapter.getPositionMode())
    }

    @Test
    fun `getTradeHistory limits and filters`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, quantity = 1.0))
        adapter.setMarkPrice("ETHUSDT", 10.0)
        adapter.placeOrder(OrderRequest("ETHUSDT", OrderSide.BUY, OrderType.MARKET, quantity = 1.0))
        assertEquals(2, adapter.getTradeHistory().size)
        assertEquals(1, adapter.getTradeHistory("BTCUSDT", 10).size)
    }

    @Test
    fun `effectiveTrading uses paper per panel flag`() = runTest {
        val provider = FakeProvider()
        assertEquals(PaperTrading.adapter, provider.effectiveTrading(paper = true))
        assertEquals(null, provider.effectiveTrading(paper = false))
    }

    @Test
    fun `order updates flow emits OPEN then CANCELED`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        val updates = mutableListOf<Order>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            adapter.subscribeToOrders()!!.collect { updates += it }
        }
        val response = adapter.placeOrder(
            OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, 1.0, price = 95.0)
        )
        adapter.cancelOrder(response.orderId)
        testScheduler.advanceUntilIdle()

        assertEquals(2, updates.size)
        assertEquals(OrderStatus.OPEN, updates[0].status)
        assertEquals(response.orderId, updates[0].orderId)
        assertEquals(OrderStatus.CANCELED, updates[1].status)
        assertEquals(response.orderId, updates[1].orderId)
    }

    @Test
    fun `order updates flow emits FILLED when limit crosses mark`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        val updates = mutableListOf<Order>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            adapter.subscribeToOrders()!!.collect { updates += it }
        }
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, 1.0, price = 95.0))
        adapter.setMarkPrice("BTCUSDT", 94.0)
        testScheduler.advanceUntilIdle()

        assertEquals(2, updates.size)
        assertEquals(OrderStatus.OPEN, updates[0].status)
        assertEquals(OrderStatus.FILLED, updates[1].status)
    }

    // ── Engine v2: типы ордеров, позиции, маржа, TP/SL, balance, persist ──

    @Test
    fun `post only crossing is rejected, non crossing rests`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        val crossing = adapter.placeOrder(
            OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.POST_ONLY, 1.0, price = 101.0)
        )
        assertFalse(crossing.success)
        val resting = adapter.placeOrder(
            OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.POST_ONLY, 1.0, price = 99.0)
        )
        assertTrue(resting.success)
        assertEquals(1, adapter.getOpenOrders().size)
    }

    @Test
    fun `ioc and fok are all or nothing`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        // не маркетабельные — отклоняются
        assertFalse(
            adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.IOC, 1.0, price = 99.0)).success
        )
        assertFalse(
            adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.SELL, OrderType.FOK, 1.0, price = 101.0)).success
        )
        assertEquals(0, adapter.getOpenOrders().size)
        // маркетабельные — taker-филл по mark
        val ioc = adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.IOC, 1.0, price = 101.0))
        assertTrue(ioc.success)
        assertEquals(100.0, adapter.getPositions()[0].avgPrice)
        val fok = adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.SELL, OrderType.FOK, 1.0, price = 95.0))
        assertTrue(fok.success)
    }

    @Test
    fun `one way mode reverses position`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, 1.0))
        adapter.setMarkPrice("BTCUSDT", 110.0)
        // SELL 2 > long 1: закрывает лонг и открывает шорт на 1
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.SELL, OrderType.MARKET, 2.0))

        val pos = adapter.portfolio().positions
        assertEquals(1, pos.size)
        assertEquals(TradeSide.SELL, pos[0].side)
        assertEquals(1.0, pos[0].quantity)
        assertEquals(110.0, pos[0].avgPrice)
        assertEquals(10.0, adapter.portfolio().history.last().pnl)
        // available: 10000 - 100 (маржа лонга) + 100 + 10 (закрытие) - 110 (маржа шорта)
        assertEquals(9900.0, usdtBalance(adapter))
    }

    @Test
    fun `hedge mode keeps separate long and short`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        assertTrue(adapter.setPositionMode(1))
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, 1.0))
        adapter.setMarkPrice("BTCUSDT", 110.0)
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.SELL, OrderType.MARKET, 1.0))
        assertEquals(2, adapter.portfolio().positions.size)
    }

    @Test
    fun `leverage reduces required margin`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.placeOrder(
            OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, 1.0, leverage = 5)
        )
        val pos = adapter.portfolio().positions[0]
        assertEquals(5, pos.leverage)
        assertEquals(9980.0, usdtBalance(adapter)) // 10000 - 100/5
    }

    @Test
    fun `reduce only caps by position and rejects without one`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, 1.0))
        adapter.setMarkPrice("BTCUSDT", 110.0)
        val capped = adapter.placeOrder(
            OrderRequest("BTCUSDT", OrderSide.SELL, OrderType.MARKET, 2.0, reduceOnly = true)
        )
        assertTrue(capped.success)
        assertEquals(0, adapter.portfolio().positions.size)
        assertEquals(1.0, adapter.portfolio().history.last().quantity)
        val noPos = adapter.placeOrder(
            OrderRequest("BTCUSDT", OrderSide.SELL, OrderType.MARKET, 1.0, reduceOnly = true)
        )
        assertFalse(noPos.success)
    }

    @Test
    fun `taker and maker fees are applied from provider rates`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.setFeeRates("BTCUSDT", com.aandios.nous.api.market.model.trading.FeeRates(maker = 0.0001, taker = 0.0004))

        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, 1.0))
        assertEquals(0.04, adapter.portfolio().history.last().fee, 1e-9)

        val resting = adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, 1.0, price = 90.0))
        assertTrue(resting.success)
        adapter.cancelOrder(resting.orderId) // очистим, чтобы не мешала
        val maker = adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.SELL, OrderType.LIMIT, 1.0, price = 90.0))
        adapter.cancelOrder(maker.orderId) // снимем SELL-рест, чтобы не исполнился на падении
        val makerRest = adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, 1.0, price = 89.0))
        assertTrue(makerRest.success)
        adapter.setMarkPrice("BTCUSDT", 89.0)
        assertEquals(0.0089, adapter.portfolio().history.last().fee, 1e-9)
    }

    @Test
    fun `take profit closes position on trigger`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.placeOrder(
            OrderRequest(
                "BTCUSDT", OrderSide.BUY, OrderType.MARKET, 1.0,
                takeProfitPrice = 110.0,
            )
        )
        adapter.setMarkPrice("BTCUSDT", 111.0)
        assertEquals(0, adapter.portfolio().positions.size)
        assertEquals(11.0, adapter.portfolio().history.last().pnl)
    }

    @Test
    fun `stop loss closes position on trigger`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.placeOrder(
            OrderRequest(
                "BTCUSDT", OrderSide.BUY, OrderType.MARKET, 1.0,
                stopLossPrice = 95.0,
            )
        )
        adapter.setMarkPrice("BTCUSDT", 94.0)
        assertEquals(0, adapter.portfolio().positions.size)
        assertEquals(-6.0, adapter.portfolio().history.last().pnl)
    }

    @Test
    fun `set balance, reset balance keeps history, reset history clears it`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setBalance("USDT", 500.0)
        assertEquals(500.0, usdtBalance(adapter))

        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, 1.0))
        assertTrue(adapter.portfolio().history.isNotEmpty())

        adapter.resetBalance(7000.0)
        assertEquals(7000.0, usdtBalance(adapter))
        assertEquals(0, adapter.portfolio().positions.size)
        assertTrue(adapter.portfolio().history.isNotEmpty())

        adapter.resetHistory()
        assertTrue(adapter.portfolio().history.isEmpty())
    }

    @Test
    fun `snapshot and restore round trip`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.setLeverage("BTCUSDT", 5)
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, 1.0))
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.SELL, OrderType.LIMIT, 1.0, price = 130.0))
        val state = adapter.snapshot()

        val restored = PaperTradingAdapter()
        restored.restore(state)
        assertEquals(state.balances, restored.getBalances())
        assertEquals(state.positions, restored.getPositions())
        assertEquals(state.openOrders, restored.getOpenOrders())
        assertEquals(state.history, restored.portfolio().history)
        assertEquals(5, restored.getLeverage("BTCUSDT"))
        assertEquals(2, restored.getPositionMode())
    }

    @Test
    fun `position and balance flows emit on fills`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        val positions = mutableListOf<Position>()
        val balances = mutableListOf<com.aandios.nous.api.market.model.Balance>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            adapter.subscribeToPositions()!!.collect { positions += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            adapter.subscribeToBalances()!!.collect { balances += it }
        }
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.MARKET, 1.0))
        testScheduler.advanceUntilIdle()
        assertTrue(positions.any { it.quantity == 1.0 })
        assertTrue(balances.isNotEmpty())

        adapter.setMarkPrice("BTCUSDT", 105.0)
        adapter.closePosition("BTCUSDT")
        testScheduler.advanceUntilIdle()
        assertTrue(positions.any { it.quantity == 0.0 })
    }

    @Test
    fun `feedMarkPrice fills resting limit`() = runBlocking {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, 1.0, price = 90.0))
        assertEquals(1, adapter.getOpenOrders().size)

        adapter.feedMarkPrice("BTCUSDT", 89.0)
        withTimeout(3000) {
            while (adapter.getOpenOrders().isNotEmpty()) delay(10)
        }
        assertEquals(0, adapter.getOpenOrders().size)
    }

    @Test
    fun `resting limit with insufficient margin is rejected at placement`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        adapter.setBalance("USDT", 100.0)
        // 10 x 100 = 1000 маржи (1x) > 100 доступных
        val response = adapter.placeOrder(
            OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, 10.0, price = 95.0)
        )
        assertFalse(response.success)
        assertTrue(response.message?.contains("Insufficient margin") == true)
        assertEquals(0, adapter.getOpenOrders().size)
    }

    @Test
    fun `resting limit rejected when margin disappears before fill`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("BTCUSDT", 100.0)
        val updates = mutableListOf<Order>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            adapter.subscribeToOrders()!!.collect { updates += it }
        }
        // маржи хватает при постановке
        assertTrue(
            adapter.placeOrder(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, 1.0, price = 95.0)).success
        )
        // но до пересечения доступный баланс исчез
        adapter.setBalance("USDT", 1.0)
        adapter.setMarkPrice("BTCUSDT", 94.0)
        testScheduler.advanceUntilIdle()

        assertEquals(0, adapter.getOpenOrders().size)
        assertEquals(0, adapter.portfolio().positions.size)
        assertTrue(updates.any { it.status == OrderStatus.REJECTED })
    }

    @Test
    fun `resting limit with sufficient margin fills and opens position`() = runTest {
        val adapter = PaperTradingAdapter()
        adapter.setMarkPrice("SOLUSDT", 120.0)
        // 10 x 119 / 10x = 119 маржи при 10000 доступных — ок
        val response = adapter.placeOrder(
            OrderRequest("SOLUSDT", OrderSide.BUY, OrderType.LIMIT, 10.0, price = 119.0, leverage = 10)
        )
        assertTrue(response.success)
        assertEquals(1, adapter.getOpenOrders().size)

        adapter.setMarkPrice("SOLUSDT", 118.0)
        val pos = adapter.portfolio().positions.single()
        assertEquals(10.0, pos.quantity)
        assertEquals(119.0, pos.avgPrice)
        assertEquals(10, pos.leverage)
        assertTrue(adapter.portfolio().history.isNotEmpty())
    }

    private class FakeProvider : com.aandios.nous.api.market.Provider {
        override val providerId = "fake"
        override val providerName = "fake"
        override val version = "1"
        override val config = com.aandios.nous.api.market.ProviderConfig(displayName = "Fake")
        override val networkManager: com.aandios.nous.api.market.NetworkManager get() = error("n/a")
        override val trades = null
        override val dom = null
        override val bookTicker = null
        override val chart = null
        override val trading = null
        override val symbolInfo = null
        override val liquidation = null
    }
}
