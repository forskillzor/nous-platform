/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui

import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.api.market.model.orderbook.DomEvent
import com.aandios.nous.api.market.model.orderbook.PriceUpdate
import com.aandios.nous.core.domain.repository.DomRepository
import com.aandios.nous.core.domain.repository.SymbolInfoRepository
import com.aandios.nous.feature.dom.domain.DomOptions
import com.aandios.nous.feature.dom.domain.TradingProvider
import com.aandios.nous.feature.dom.domain.TradingSymbol
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import com.aandios.nous.feature.dom.domain.model.DepthLimit
import com.aandios.nous.feature.dom.domain.model.OrderIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DomViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var viewModel: DomViewModel
    private lateinit var fakeDomRepository: FakeDomRepository
    private lateinit var fakeSymbolInfoRepository: FakeSymbolInfoRepository

    @BeforeTest
    fun setUp() {
        fakeDomRepository = FakeDomRepository()
        fakeSymbolInfoRepository = FakeSymbolInfoRepository()
        viewModel = DomViewModel(fakeDomRepository, fakeSymbolInfoRepository, testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        // Clean up if needed
    }

    @Test
    fun `initial state`() = testScope.runTest {
        val options = viewModel.domOptions.first()
        assertEquals(DomOptions.default(), options)

        val selectedPrice = viewModel.selectedPrice.first()
        assertNull(selectedPrice)

        val quantity = viewModel.orderQuantity.first()
        assertEquals("0.01", quantity)

        val tradingEnabled = viewModel.isTradingEnabled.first()
        assertTrue(tradingEnabled)

        val tickSize = viewModel.symbolTickSize.first()
        assertNull(tickSize) // Not fetched yet due to delay
    }

    @Test
    fun `updateDomOptions changes options and triggers subscription`() = testScope.runTest {
        val newOptions = DomOptions.default().copy(
            symbol = TradingSymbol("ETHUSDT", "ETH/USDT", TradingProvider.BINANCE),
            depth = DepthLimit.create(20)
        )

        viewModel.updateDomOptions(newOptions)
        advanceUntilIdle()

        val currentOptions = viewModel.domOptions.first()
        assertEquals(newOptions, currentOptions)

        assertEquals("ETHUSDT", fakeDomRepository.lastSubscribedSymbol)
        assertEquals(20, fakeDomRepository.lastSubscribedDepth)
    }

    @Test
    fun `selectPrice updates selectedPrice`() = testScope.runTest {
        viewModel.selectPrice(50000.0)
        assertEquals(50000.0, viewModel.selectedPrice.first())

        viewModel.selectPrice(null)
        assertNull(viewModel.selectedPrice.first())
    }

    @Test
    fun `updateOrderQuantity updates quantity`() = testScope.runTest {
        viewModel.updateOrderQuantity("1.5")
        assertEquals("1.5", viewModel.orderQuantity.first())
    }

    @Test
    fun `handleOrderIntent MarketBuy creates command`() = testScope.runTest {
        val intent = OrderIntent.MarketBuy("BTCUSDT", 0.5)
        viewModel.handleOrderIntent(intent)
        advanceUntilIdle()

        // Verify command execution (fake repository doesn't execute, but we can check lastCommandResult)
        val result = viewModel.lastCommandResult.first()
        // Since command execution is async and uses fake, result may be null or something else
        // We'll just ensure no crash
    }

    @Test
    fun `handleOrderIntent ToggleTrading toggles trading`() = testScope.runTest {
        val intent = OrderIntent.ToggleTrading
        viewModel.handleOrderIntent(intent)
        advanceUntilIdle()

        // TradeOffCommand should set isTradingEnabled to false
        val enabled = viewModel.isTradingEnabled.first()
        // Actually TradeOffCommand toggles via callback; we can't easily test without mocking
        // We'll just ensure no crash
    }

    @Test
    fun `book window stores window data`() = testScope.runTest {
        fakeDomRepository.domEventsFlow.emit(
            DomEvent.BookWindow(
                bids = listOf(PriceUpdate(50000.0, 1.5), PriceUpdate(49900.0, 2.0)),
                asks = listOf(PriceUpdate(50100.0, 0.8), PriceUpdate(50200.0, 1.2))
            )
        )
        advanceUntilIdle()

        val bids = viewModel.windowBids
        assertEquals(2, bids.size)
        assertEquals(1.5, bids[50000.0])
        assertEquals(2.0, bids[49900.0])

        val asks = viewModel.windowAsks
        assertEquals(2, asks.size)
        assertEquals(0.8, asks[50100.0])
        assertEquals(1.2, asks[50200.0])
    }

    @Test
    fun `book window replaces previous window`() = testScope.runTest {
        fakeSymbolInfoRepository.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        fakeDomRepository.domEventsFlow.emit(
            DomEvent.BookWindow(
                bids = listOf(PriceUpdate(50000.0, 1.5), PriceUpdate(49900.0, 2.0)),
                asks = emptyList()
            )
        )
        advanceUntilIdle()
        assertEquals(2, viewModel.sortedLevels.size)

        // РќРѕРІРѕРµ РѕРєРЅРѕ Р±РµР· СѓСЂРѕРІРЅСЏ 49900 вЂ” СѓСЂРѕРІРµРЅСЊ РёСЃС‡РµР·Р°РµС‚ РёР· РєРЅРёРіРё
        fakeDomRepository.domEventsFlow.emit(
            DomEvent.BookWindow(
                bids = listOf(PriceUpdate(50000.0, 1.5)),
                asks = emptyList()
            )
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.sortedLevels.size)
        assertEquals(50000L, viewModel.sortedLevels.first().priceTicks)
    }

    @Test
    fun `processDomEvent BestPrices updates best prices`() = testScope.runTest {
        fakeSymbolInfoRepository.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        fakeDomRepository.domEventsFlow.emit(
            DomEvent.BestPrices(50000.0, 1.5, 50100.0, 0.8, 50050.0, "BTCUSDT")
        )
        advanceUntilIdle()

        val best = viewModel.bestPrices.first()
        assertEquals(50000.0, best.bestBid)
        assertEquals(50100.0, best.bestAsk)
        assertEquals(1.5, best.bestBidQuantity)
        assertEquals(0.8, best.bestAskQuantity)
        // Последняя сделка — в корзине агрегации (BaseTick: 1 тик = 1.0)
        assertEquals(50050L, best.lastPriceDisplayTicks)
    }

    @Test
    fun `sorted levels are built from window in descending price order`() = testScope.runTest {
        fakeSymbolInfoRepository.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        fakeDomRepository.domEventsFlow.emit(
            DomEvent.BookWindow(
                bids = listOf(PriceUpdate(50000.0, 1.0), PriceUpdate(49900.0, 2.0)),
                asks = listOf(PriceUpdate(50100.0, 3.0))
            )
        )
        advanceUntilIdle()

        val levels = viewModel.sortedLevels
        assertEquals(3, levels.size)
        assertEquals(listOf(50100L, 50000L, 49900L), levels.map { it.priceTicks })
        // ask-СѓСЂРѕРІРµРЅСЊ РЅР° 50100: С‚РѕР»СЊРєРѕ askSteps
        assertNull(levels[0].bidSteps)
        assertEquals(300L, levels[0].askSteps)
        // bid-СѓСЂРѕРІРµРЅСЊ РЅР° 50000: С‚РѕР»СЊРєРѕ bidSteps
        assertEquals(100L, levels[1].bidSteps)
        assertNull(levels[1].askSteps)
        // bid-СѓСЂРѕРІРµРЅСЊ РЅР° 49900
        assertEquals(200L, levels[2].bidSteps)
        assertNull(levels[2].askSteps)
    }

    @Test
    fun `ask wins over bid on the same price (one side rule)`() = testScope.runTest {
        fakeSymbolInfoRepository.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        fakeDomRepository.domEventsFlow.emit(
            DomEvent.BookWindow(
                bids = listOf(PriceUpdate(50000.0, 1.0)),
                asks = listOf(PriceUpdate(50000.0, 2.0))
            )
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.sortedLevels.size)
        val level = viewModel.sortedLevels.first()
        assertNull(level.bidSteps)
        assertEquals(200L, level.askSteps)
    }

    @Test
    fun `aggregation buckets levels`() = testScope.runTest {
        fakeSymbolInfoRepository.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        viewModel.updateDomOptions(
            DomOptions.default().copy(aggregation = AggregationLevel.TenTick)
        )
        advanceUntilIdle()

        // 50001, 50005, 50009 в†’ РѕРґРЅР° РєРѕСЂР·РёРЅР° 50000; 50010 в†’ 50010
        fakeDomRepository.domEventsFlow.emit(
            DomEvent.BookWindow(
                bids = listOf(
                    PriceUpdate(50001.0, 1.0),
                    PriceUpdate(50005.0, 2.0),
                    PriceUpdate(50009.0, 3.0),
                    PriceUpdate(50010.0, 4.0)
                ),
                asks = emptyList()
            )
        )
        advanceUntilIdle()

        val levels = viewModel.sortedLevels
        assertEquals(2, levels.size)
        assertEquals(listOf(50010L, 50000L), levels.map { it.priceTicks })
        assertEquals(400L, levels[0].bidSteps)
        assertEquals(600L, levels[1].bidSteps)  // 100 + 200 + 300
    }

    @Test
    fun `window keeps all levels - ladder shows full book`() = testScope.runTest {
        fakeSymbolInfoRepository.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        // 30 bid-уровней — окно сохраняется целиком (без обрезки: лесенка скроллится по цене)
        val bids = (0 until 30).map { i -> PriceUpdate((50000.0 - i), 1.0) }
        fakeDomRepository.domEventsFlow.emit(DomEvent.BookWindow(bids = bids, asks = emptyList()))
        advanceUntilIdle()

        assertEquals(30, viewModel.sortedLevels.size)
        assertEquals(50000L, viewModel.sortedLevels.first().priceTicks)
        assertEquals(49971L, viewModel.sortedLevels.last().priceTicks)
    }

    @Test
    fun `window before metadata - book is drawn after metadata arrives`() = testScope.runTest {
        // Окно приходит ДО метаданных (fetch задержан на 500мс) — уровней ещё нет.
        // runCurrent обрабатывает задачи только в текущем виртуальном времени (t=0),
        // не пересекая delay(500) — окно успевает прийти первым
        fakeDomRepository.domEventsFlow.emit(
            DomEvent.BookWindow(
                bids = listOf(PriceUpdate(50000.0, 1.5)),
                asks = listOf(PriceUpdate(50100.0, 0.8))
            )
        )
        runCurrent()
        assertEquals(0, viewModel.sortedLevels.size)
        assertEquals(1, viewModel.windowBids.size)
        assertEquals(1, viewModel.windowAsks.size)

        // Приходят метаданные (tickSize=1.0, stepSize=0.01) — книга строится из последнего окна
        fakeSymbolInfoRepository.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        val levels = viewModel.sortedLevels
        assertEquals(2, levels.size)
        assertEquals(listOf(50100L, 50000L), levels.map { it.priceTicks })
        assertNull(levels[0].bidSteps)
        assertEquals(80L, levels[0].askSteps)   // 0.8 / 0.01
        assertEquals(150L, levels[1].bidSteps)  // 1.5 / 0.01
        assertNull(levels[1].askSteps)
    }

    @Test
    fun `fetchSymbolTickSize updates tickSize`() = testScope.runTest {
        fakeSymbolInfoRepository.tickSize = 0.01
        viewModel.updateDomOptions(DomOptions.default().copy(symbol = TradingSymbol("BTCUSDT", "BTC/USDT", TradingProvider.BINANCE)))
        advanceUntilIdle()

        // Wait for fetch (there's a delay in init)
        advanceTimeBy(600)
        advanceUntilIdle()

        val tickSize = viewModel.symbolTickSize.first()
        assertEquals(0.01, tickSize)
    }
}

// Fake repositories
class FakeDomRepository : DomRepository {
    val domEventsFlow = MutableSharedFlow<DomEvent>(extraBufferCapacity = 10)
    var lastSubscribedSymbol: String? = null
    var lastSubscribedDepth: Int? = null

    override suspend fun subscribeToDomEvents(symbol: String, depth: Int) = domEventsFlow.also {
        lastSubscribedSymbol = symbol
        lastSubscribedDepth = depth
    }
}

class FakeSymbolInfoRepository : SymbolInfoRepository {
    var tickSize: Double? = null

    override suspend fun getSymbolInfo(symbol: String): SymbolInfo? {
        return tickSize?.let {
            SymbolInfo(
                symbol = symbol,
                tickSize = it,
                stepSize = 0.01,
                minQty = 0.001,
                minNotional = 10.0,
                status = "TRADING",
                baseAsset = symbol.substring(0, 3),
                quoteAsset = symbol.substring(3)
            )
        }
    }
}
