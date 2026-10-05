/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.paper

import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `effectiveTrading uses paper when enabled`() = runTest {
        val provider = FakeProvider()
        PaperTrading.enabled = true
        try {
            assertEquals(PaperTrading.adapter, provider.effectiveTrading())
        } finally {
            PaperTrading.enabled = false
        }
        assertEquals(null, provider.effectiveTrading())
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
