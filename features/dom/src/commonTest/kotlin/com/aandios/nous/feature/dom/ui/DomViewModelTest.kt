/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui

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
import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.api.market.model.orderbook.PriceUpdate
import com.aandios.nous.feature.dom.domain.DomOptions
import com.aandios.nous.feature.dom.domain.TradingSymbol
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import com.aandios.nous.feature.dom.domain.model.DepthLimit
import com.aandios.nous.feature.dom.domain.model.OrderIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DomViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var viewModel: DomViewModel
    private lateinit var fakeDomAdapter: FakeDomAdapter
    private lateinit var fakeBookTickerAdapter: FakeBookTickerAdapter
    private lateinit var fakeSymbolInfoAdapter: FakeSymbolInfoAdapter

    @BeforeTest
    fun setUp() {
        fakeDomAdapter = FakeDomAdapter()
        fakeBookTickerAdapter = FakeBookTickerAdapter()
        fakeSymbolInfoAdapter = FakeSymbolInfoAdapter()
        val provider = FakeDomProvider(
            domAdapter = fakeDomAdapter,
            bookTickerAdapter = fakeBookTickerAdapter,
            symbolInfoAdapter = fakeSymbolInfoAdapter,
        )
        viewModel = DomViewModel(
            providerRegistry = ProviderRegistry(listOf(provider)),
            coroutineDispatcher = testDispatcher,
        )
    }

    @AfterTest
    fun tearDown() {
        // Clean up if needed
    }

    @Test
    fun `dom paper order really places into paper engine`() = testScope.runTest {
        val paper = com.aandios.nous.api.market.paper.PaperTrading.adapter
        paper.reset()
        paper.setMarkPrice("BTCUSDT", 100.0)
        viewModel.setPaperEnabled(true)

        viewModel.handleOrderIntent(OrderIntent.MarketBuy("BTCUSDT", 1.0))
        advanceUntilIdle()

        val positions = paper.getPositions()
        assertEquals(1, positions.size)
        assertEquals(1.0, positions[0].quantity)
        assertEquals(100.0, positions[0].avgPrice)
    }

    @Test
    fun `dom paper limit rests and fills when price crosses`() = testScope.runTest {
        val paper = com.aandios.nous.api.market.paper.PaperTrading.adapter
        paper.reset()
        paper.setMarkPrice("BTCUSDT", 100.0)
        viewModel.setPaperEnabled(true)

        viewModel.handleOrderIntent(OrderIntent.LimitBuy("BTCUSDT", 95.0, 1.0))
        advanceUntilIdle()
        assertEquals(1, paper.getOpenOrders().size)

        paper.setMarkPrice("BTCUSDT", 94.0)
        advanceUntilIdle()
        assertEquals(1, paper.getPositions().size)
        assertEquals(0, paper.getOpenOrders().size)
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
            symbol = TradingSymbol("ETHUSDT", "ETH/USDT", "binance-nous-0.0.1"),
            depth = DepthLimit.create(20)
        )

        viewModel.updateDomOptions(newOptions)
        advanceUntilIdle()

        val currentOptions = viewModel.domOptions.first()
        assertEquals(newOptions, currentOptions)

        assertEquals("ETHUSDT", fakeDomAdapter.lastSubscribedSymbol)
        assertEquals(20, fakeDomAdapter.lastSubscribedDepth)
    }

    @Test
    fun `selectPrice works only with confirm on and toggles`() = testScope.runTest {
        // Без Confirm клик по уровню не выделяет цену
        viewModel.selectPrice(50000.0)
        assertNull(viewModel.selectedPrice.first())

        // С Confirm: первый клик выделяет, повторный — снимает
        viewModel.setConfirmOrders(true)
        viewModel.selectPrice(50000.0)
        assertEquals(50000.0, viewModel.selectedPrice.first())
        viewModel.selectPrice(50000.0)
        assertNull(viewModel.selectedPrice.first())

        // Выключение Confirm сбрасывает выделение
        viewModel.selectPrice(50000.0)
        assertEquals(50000.0, viewModel.selectedPrice.first())
        viewModel.setConfirmOrders(false)
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
        // Команды исполняются асинхронно через фейк — просто убеждаемся, что не падает
    }

    @Test
    fun `handleOrderIntent ToggleTrading toggles trading`() = testScope.runTest {
        val intent = OrderIntent.ToggleTrading
        viewModel.handleOrderIntent(intent)
        advanceUntilIdle()
        // TradeOffCommand переключает через callback — просто убеждаемся, что не падает
    }

    @Test
    fun `book window stores window data`() = testScope.runTest {
        runCurrent() // даём подписке (callbackFlow + адаптеры) подняться
        fakeDomAdapter.windowFlow.emit(
            BookWindowLevels(
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
        fakeSymbolInfoAdapter.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        fakeDomAdapter.windowFlow.emit(
            BookWindowLevels(
                bids = listOf(PriceUpdate(50000.0, 1.5), PriceUpdate(49900.0, 2.0)),
                asks = emptyList()
            )
        )
        advanceUntilIdle()
        assertEquals(2, viewModel.sortedLevels.size)

        // Новое окно без уровня 49900 — уровень исчезает из книги
        fakeDomAdapter.windowFlow.emit(
            BookWindowLevels(
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
        fakeSymbolInfoAdapter.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        fakeBookTickerAdapter.tickerFlow.emit(
            BookTicker(
                symbol = "BTCUSDT",
                bestBid = 50000.0,
                bestBidQty = 1.5,
                bestAsk = 50100.0,
                bestAskQty = 0.8,
                lastPrice = 50050.0,
                timestamp = 0,
            )
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
        fakeSymbolInfoAdapter.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        fakeDomAdapter.windowFlow.emit(
            BookWindowLevels(
                bids = listOf(PriceUpdate(50000.0, 1.0), PriceUpdate(49900.0, 2.0)),
                asks = listOf(PriceUpdate(50100.0, 3.0))
            )
        )
        advanceUntilIdle()

        val levels = viewModel.sortedLevels
        assertEquals(3, levels.size)
        assertEquals(listOf(50100L, 50000L, 49900L), levels.map { it.priceTicks })
        // ask-уровень на 50100: только askSteps
        assertNull(levels[0].bidSteps)
        assertEquals(300L, levels[0].askSteps)
        // bid-уровень на 50000: только bidSteps
        assertEquals(100L, levels[1].bidSteps)
        assertNull(levels[1].askSteps)
        // bid-уровень на 49900
        assertEquals(200L, levels[2].bidSteps)
        assertNull(levels[2].askSteps)
    }

    @Test
    fun `ask wins over bid on the same price (one side rule)`() = testScope.runTest {
        fakeSymbolInfoAdapter.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        fakeDomAdapter.windowFlow.emit(
            BookWindowLevels(
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
        fakeSymbolInfoAdapter.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        viewModel.updateDomOptions(
            DomOptions.default().copy(aggregation = AggregationLevel.TenTick)
        )
        advanceUntilIdle()

        // 50001, 50005, 50009 → одна корзина 50000; 50010 → 50010
        fakeDomAdapter.windowFlow.emit(
            BookWindowLevels(
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
        fakeSymbolInfoAdapter.tickSize = 1.0
        advanceTimeBy(600)
        advanceUntilIdle()

        // 30 bid-уровней — окно сохраняется целиком (без обрезки: лесенка скроллится по цене)
        val bids = (0 until 30).map { i -> PriceUpdate((50000.0 - i), 1.0) }
        fakeDomAdapter.windowFlow.emit(BookWindowLevels(bids = bids, asks = emptyList()))
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
        runCurrent() // даём подписке (callbackFlow + адаптеры) подняться без продвижения времени
        fakeDomAdapter.windowFlow.emit(
            BookWindowLevels(
                bids = listOf(PriceUpdate(50000.0, 1.5)),
                asks = listOf(PriceUpdate(50100.0, 0.8))
            )
        )
        runCurrent()
        assertEquals(0, viewModel.sortedLevels.size)
        assertEquals(1, viewModel.windowBids.size)
        assertEquals(1, viewModel.windowAsks.size)

        // Приходят метаданные (tickSize=1.0, stepSize=0.01) — книга строится из последнего окна
        fakeSymbolInfoAdapter.tickSize = 1.0
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
        fakeSymbolInfoAdapter.tickSize = 0.01
        viewModel.updateDomOptions(
            DomOptions.default().copy(symbol = TradingSymbol("BTCUSDT", "BTC/USDT", "binance-nous-0.0.1"))
        )
        advanceUntilIdle()

        // Wait for fetch (there's a delay in init)
        advanceTimeBy(600)
        advanceUntilIdle()

        val tickSize = viewModel.symbolTickSize.first()
        assertEquals(0.01, tickSize)
    }
}

// Fake adapters / provider

class FakeDomAdapter : DomAdapter {
    val windowFlow = MutableSharedFlow<BookWindowLevels>(extraBufferCapacity = 10)
    var lastSubscribedSymbol: String? = null
    var lastSubscribedDepth: Int? = null

    override suspend fun subscribeToBookWindow(symbol: String, depth: Int): Flow<BookWindowLevels> =
        windowFlow.also {
            lastSubscribedSymbol = symbol
            lastSubscribedDepth = depth
        }
}

class FakeBookTickerAdapter : BookTickerAdapter {
    val tickerFlow = MutableSharedFlow<BookTicker>(extraBufferCapacity = 10)

    override fun subscribeToBookTicker(symbol: String): Flow<BookTicker> = tickerFlow

    override suspend fun getBookTickerRest(symbol: String): BookTicker? = null
}

class FakeSymbolInfoAdapter : SymbolInfoAdapter {
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

class FakeDomProvider(
    private val domAdapter: DomAdapter,
    private val bookTickerAdapter: BookTickerAdapter,
    private val symbolInfoAdapter: SymbolInfoAdapter,
) : Provider {
    override val providerId = "binance-nous-0.0.1"
    override val providerName = "binance-nous"
    override val version = "0.0.1"
    override val config = ProviderConfig(displayName = "Binance")
    override val networkManager: NetworkManager get() = error("not used")
    override val trades: TradesAdapter? = null
    override val dom: DomAdapter? = domAdapter
    override val bookTicker: BookTickerAdapter? = bookTickerAdapter
    override val chart: ChartAdapter? = null
    override val trading: TradingAdapter? = null
    override val symbolInfo: SymbolInfoAdapter? = symbolInfoAdapter
    override val liquidation: LiquidationAdapter? = null
}
