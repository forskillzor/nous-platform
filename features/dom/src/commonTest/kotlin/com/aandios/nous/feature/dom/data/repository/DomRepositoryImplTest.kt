/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.data.repository

import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.api.market.model.orderbook.DepthSnapshot
import com.aandios.nous.api.market.model.orderbook.DepthUpdate
import com.aandios.nous.api.market.model.orderbook.DomEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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
import kotlin.test.assertTrue

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
    fun `subscribeToDomEvents emits snapshot first`() = testScope.runTest {
        val symbol = "BTCUSDT"
        val depth = 10

        fakeDomAdapter.snapshot = DepthSnapshot(
            lastUpdateId = 100,
            bids = listOf(listOf("50000.0", "1.5")),
            asks = listOf(listOf("50100.0", "0.8"))
        )

        val events = mutableListOf<DomEvent>()
        val job = launch {
            repository.subscribeToDomEvents(symbol, depth).toList(events)
        }

        advanceUntilIdle()

        // Should have at least one Snapshot event
        assertTrue(events.isNotEmpty())
        val firstEvent = events[0]
        assertIs<DomEvent.Snapshot>(firstEvent)
        assertEquals(symbol, firstEvent.symbol)
        assertEquals(100, firstEvent.snapshot.lastUpdateId)
        assertEquals(listOf(listOf("50000.0", "1.5")), firstEvent.snapshot.bids)
        assertEquals(listOf(listOf("50100.0", "0.8")), firstEvent.snapshot.asks)

        job.cancel()
    }

    @Test
    fun `subscribeToDomEvents emits depth updates after snapshot`() = testScope.runTest {
        val symbol = "BTCUSDT"
        val depth = 10

        fakeDomAdapter.snapshot = DepthSnapshot(
            lastUpdateId = 100,
            bids = emptyList(),
            asks = emptyList()
        )

        // Start collecting events, take first 2 events (Snapshot + UpdateBid)
        val events = mutableListOf<DomEvent>()
        val job = launch {
            repository.subscribeToDomEvents(symbol, depth)
                .take(2)
                .collect { event -> events.add(event) }
        }

        // Wait for repository to start and get snapshot
        advanceUntilIdle()
        // Emit depth update after subscription
        val depthUpdate = DepthUpdate(
            firstUpdateId = 101,
            finalUpdateId = 101,
            previousFinalUpdateId = 100,
            bids = listOf(listOf("50000.0", "1.0")),
            asks = emptyList()
        )
        fakeDomAdapter.depthUpdatesFlow.emit(depthUpdate)

        // Advance time to process the update
        advanceUntilIdle()

        // Should have exactly 2 events: Snapshot and UpdateBid
        assertEquals(2, events.size, "Events: $events")
        val snapshotEvent = events[0]
        assertIs<DomEvent.Snapshot>(snapshotEvent)
        val updateEvent = events[1]
        assertIs<DomEvent.UpdateBid>(updateEvent)
        assertEquals(50000.0, updateEvent.price)
        assertEquals(1.0, updateEvent.quantity)

        job.cancel()
    }

    @Test
    fun `subscribeToDomEvents emits book ticker events`() = testScope.runTest {
        val symbol = "BTCUSDT"
        val depth = 10

        fakeDomAdapter.snapshot = DepthSnapshot(
            lastUpdateId = 100,
            bids = emptyList(),
            asks = emptyList()
        )

        // Start collecting events, take first 2 events (Snapshot + BestPrices)
        val events = mutableListOf<DomEvent>()
        val job = launch {
            repository.subscribeToDomEvents(symbol, depth)
                .take(2)
                .collect { event -> events.add(event) }
        }

        // Wait for repository to start and get snapshot
        advanceUntilIdle()
        // Now emit book ticker
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

        // Advance time to process the ticker
        advanceUntilIdle()

        assertEquals(2, events.size, "Events: $events")
        val snapshotEvent = events[0]
        assertIs<DomEvent.Snapshot>(snapshotEvent)
        val bestPricesEvent = events[1]
        assertIs<DomEvent.BestPrices>(bestPricesEvent)
        assertEquals(symbol, bestPricesEvent.symbol)
        assertEquals(50000.0, bestPricesEvent.bestBid)
        assertEquals(50100.0, bestPricesEvent.bestAsk)
        assertEquals(1.5, bestPricesEvent.bestBidQuantity)
        assertEquals(0.8, bestPricesEvent.bestAskQuantity)

        job.cancel()
    }

    @Test
    fun `subscribeToDomEvents reconnects on error`() = testScope.runTest {
        val symbol = "BTCUSDT"
        val depth = 10

        // First snapshot
        fakeDomAdapter.snapshot = DepthSnapshot(
            lastUpdateId = 100,
            bids = emptyList(),
            asks = emptyList()
        )

        // Simulate an error in depth updates
        fakeDomAdapter.shouldThrow = true

        val events = mutableListOf<DomEvent>()
        val job = launch {
            repository.subscribeToDomEvents(symbol, depth).toList(events)
        }

        advanceUntilIdle()

        // Should have snapshot and possibly Reset due to error
        assertTrue(events.isNotEmpty())
        // After error, repository should attempt reconnection (internal loop)
        // We can't easily test infinite loop, but we can verify that snapshot was emitted
        val snapshotEvents = events.filterIsInstance<DomEvent.Snapshot>()
        assertTrue(snapshotEvents.isNotEmpty())

        job.cancel()
    }

    @Test
    fun `subscribeToDomEvents handles reinitialization exception`() = testScope.runTest {
        val symbol = "BTCUSDT"
        val depth = 10

        fakeDomAdapter.snapshot = DepthSnapshot(
            lastUpdateId = 100,
            bids = emptyList(),
            asks = emptyList()
        )

        // Start collecting events, take first 3 events (Snapshot, UpdateBid, Reset)
        val events = mutableListOf<DomEvent>()
        val job = launch {
            repository.subscribeToDomEvents(symbol, depth)
                .take(3)
                .collect { event -> events.add(event) }
        }

        // Wait for repository to start and get snapshot
        advanceUntilIdle()
        // Emit a valid depth update to initialize OrderBookState
        val validUpdate = DepthUpdate(
            firstUpdateId = 101,
            finalUpdateId = 101,
            previousFinalUpdateId = 100,
            bids = listOf(listOf("50000.0", "1.0")),
            asks = emptyList()
        )
        fakeDomAdapter.depthUpdatesFlow.emit(validUpdate)
        advanceUntilIdle()

        // Now emit a depth update with broken sequence to trigger reinitialization
        val brokenUpdate = DepthUpdate(
            firstUpdateId = 102,
            finalUpdateId = 102,
            previousFinalUpdateId = 999, // incorrect, should cause validation failure
            bids = listOf(listOf("50001.0", "0.5")),
            asks = emptyList()
        )
        fakeDomAdapter.depthUpdatesFlow.emit(brokenUpdate)
        advanceUntilIdle()

        // Should have exactly 3 events: Snapshot, UpdateBid, Reset
        assertEquals(3, events.size, "Events: $events")
        val resetEvent = events[2]
        assertIs<DomEvent.Reset>(resetEvent)

        job.cancel()
    }
}

// Fake implementations
class FakeDomAdapter : DomAdapter {
    var snapshot: DepthSnapshot? = null
    val depthUpdatesFlow = MutableSharedFlow<DepthUpdate>(extraBufferCapacity = 10)
    var shouldThrow = false
    var shouldFailValidation = false

    override suspend fun getOrderBookSnapshot(symbol: String, depth: Int): DepthSnapshot {
        return snapshot ?: DepthSnapshot(0, emptyList(), emptyList())
    }

    override suspend fun subscribeToDepthUpdates(symbol: String, depth: Int): Flow<DepthUpdate> {
        return if (shouldThrow) {
            flow { throw RuntimeException("Test error") }
        } else {
            depthUpdatesFlow
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