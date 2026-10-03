/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.data.repository

import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.api.market.model.orderbook.DomEvent
import com.aandios.nous.api.market.model.orderbook.PriceUpdate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DomRepositoryImplTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: DomRepositoryImpl
    private lateinit var fakeDomAdapter: FakeDomAdapter
    private lateinit var fakeBookTickerAdapter: FakeBookTickerAdapter

    @BeforeTest
    fun setUp() {
        fakeDomAdapter = FakeDomAdapter()
        fakeBookTickerAdapter = FakeBookTickerAdapter()
        repository = DomRepositoryImpl(fakeDomAdapter, fakeBookTickerAdapter)
    }

    @AfterTest
    fun tearDown() {
        // Clean up if needed
    }

    @Test
    fun `subscribeToDomEvents emits book window events`() = testScope.runTest {
        val symbol = "BTCUSDT"
        val depth = 20

        val events = mutableListOf<DomEvent>()
        val job = launch {
            repository.subscribeToDomEvents(symbol, depth).take(1).collect { event -> events.add(event) }
        }
        advanceUntilIdle()

        fakeDomAdapter.windowFlow.emit(
            BookWindowLevels(
                bids = listOf(PriceUpdate(50000.0, 1.5)),
                asks = listOf(PriceUpdate(50100.0, 0.8))
            )
        )
        advanceUntilIdle()

        assertEquals(1, events.size, "Events: $events")
        val event = events[0]
        assertIs<DomEvent.BookWindow>(event)
        assertEquals(1, event.bids.size)
        assertEquals(50000.0, event.bids[0].price)
        assertEquals(1.5, event.bids[0].quantity)
        assertEquals(1, event.asks.size)
        assertEquals(50100.0, event.asks[0].price)
        assertEquals(0.8, event.asks[0].quantity)

        job.cancel()
    }

    @Test
    fun `subscribeToDomEvents emits book ticker events`() = testScope.runTest {
        val symbol = "BTCUSDT"
        val depth = 20

        val events = mutableListOf<DomEvent>()
        val job = launch {
            repository.subscribeToDomEvents(symbol, depth).take(1).collect { event -> events.add(event) }
        }
        advanceUntilIdle()

        val bookTicker = BookTicker(
            symbol = symbol,
            bestBid = 50000.0,
            bestBidQty = 1.5,
            bestAsk = 50100.0,
            bestAskQty = 0.8,
            lastPrice = 50050.0,
            timestamp = 123456789
        )
        fakeBookTickerAdapter.bookTickerFlow.emit(bookTicker)
        advanceUntilIdle()

        assertEquals(1, events.size, "Events: $events")
        val event = events[0]
        assertIs<DomEvent.BestPrices>(event)
        assertEquals(symbol, event.symbol)
        assertEquals(50000.0, event.bestBid)
        assertEquals(50100.0, event.bestAsk)
        assertEquals(1.5, event.bestBidQuantity)
        assertEquals(0.8, event.bestAskQuantity)
        assertEquals(50050.0, event.lastPrice)

        job.cancel()
    }

    @Test
    fun `reconnects after stream failure and keeps delivering events`() = testScope.runTest {
        val symbol = "BTCUSDT"
        val depth = 20

        // Первая подписка на окна падает, после реконнекта работает
        fakeDomAdapter.failuresLeft = 1

        val events = mutableListOf<DomEvent>()
        val job = launch {
            repository.subscribeToDomEvents(symbol, depth).take(1).collect { event -> events.add(event) }
        }
        // Первая попытка падает → backoff 250мс → вторая подписка активна
        advanceUntilIdle()
        assertEquals(2, fakeDomAdapter.subscribeCalls)

        fakeDomAdapter.windowFlow.emit(
            BookWindowLevels(
                bids = listOf(PriceUpdate(50000.0, 1.0)),
                asks = emptyList()
            )
        )
        advanceUntilIdle()

        assertEquals(1, events.size, "Events: $events")
        assertIs<DomEvent.BookWindow>(events[0])

        job.cancel()
    }
}

// Fake implementations
class FakeDomAdapter : DomAdapter {
    val windowFlow = MutableSharedFlow<BookWindowLevels>(extraBufferCapacity = 10)
    var failuresLeft = 0
    var subscribeCalls = 0

    override suspend fun subscribeToBookWindow(symbol: String, depth: Int): Flow<BookWindowLevels> {
        subscribeCalls++
        return if (failuresLeft > 0) {
            failuresLeft--
            flow { throw RuntimeException("Test error") }
        } else {
            windowFlow
        }
    }
}

class FakeBookTickerAdapter : BookTickerAdapter {
    val bookTickerFlow = MutableSharedFlow<BookTicker>(extraBufferCapacity = 10)

    override fun subscribeToBookTicker(symbol: String): Flow<BookTicker> {
        return bookTickerFlow
    }

    override suspend fun getBookTickerRest(symbol: String): BookTicker? {
        return null
    }
}
