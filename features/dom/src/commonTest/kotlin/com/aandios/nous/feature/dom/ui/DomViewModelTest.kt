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
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.orderbook.PriceUpdate
import com.aandios.nous.core.storage.StateStore
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
import kotlin.test.assertFalse
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
    private lateinit var fakeProvider: FakeDomProvider

    @BeforeTest
    fun setUp() {
        fakeDomAdapter = FakeDomAdapter()
        fakeBookTickerAdapter = FakeBookTickerAdapter()
        fakeSymbolInfoAdapter = FakeSymbolInfoAdapter()
        fakeProvider = FakeDomProvider(
            domAdapter = fakeDomAdapter,
            bookTickerAdapter = fakeBookTickerAdapter,
            symbolInfoAdapter = fakeSymbolInfoAdapter,
        )
        viewModel = DomViewModel(
            providerRegistry = ProviderRegistry(listOf(fakeProvider)),
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
        viewModel.setTradingEnabled(true)

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
        viewModel.setTradingEnabled(true)

        viewModel.handleOrderIntent(OrderIntent.LimitBuy("BTCUSDT", 95.0, 1.0))
        advanceUntilIdle()
        assertEquals(1, paper.getOpenOrders().size)

        paper.setMarkPrice("BTCUSDT", 94.0)
        advanceUntilIdle()
        assertEquals(1, paper.getPositions().size)
        assertEquals(0, paper.getOpenOrders().size)
    }

    @Test
    fun `click level without confirm places limit immediately`() = testScope.runTest {
        val paper = com.aandios.nous.api.market.paper.PaperTrading.adapter
        paper.reset()
        advanceUntilIdle()
        // ����� ~100: bid 99.9 / ask 100.1 ��� ����������� ������� �����
        fakeBookTickerAdapter.tickerFlow.emit(
            BookTicker(
                symbol = "BTCUSDT",
                bestBid = 99.9,
                bestBidQty = 1.0,
                bestAsk = 100.1,
                bestAskQty = 1.0,
                lastPrice = 100.0,
                timestamp = 0,
            )
        )
        advanceUntilIdle()
        viewModel.setPaperEnabled(true)
        viewModel.setTradingEnabled(true)
        viewModel.updateOrderQuantity("1.0")
        advanceUntilIdle()

        // ���� ���� ����� � BUY-������� �� ���� ������
        viewModel.selectPrice(95.0)
        advanceUntilIdle()
        val orders = paper.getOpenOrders()
        assertEquals(1, orders.size)
        assertEquals(com.aandios.nous.api.market.model.orderbook.OrderSide.BUY, orders[0].side)
        assertEquals(95.0, orders[0].price)

        // ���� ���� ����� � SELL-�������
        viewModel.selectPrice(105.0)
        advanceUntilIdle()
        assertEquals(2, paper.getOpenOrders().size)
        assertTrue(
            paper.getOpenOrders().any {
                it.side == com.aandios.nous.api.market.model.orderbook.OrderSide.SELL && it.price == 105.0
            }
        )
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
        assertFalse(tradingEnabled) // по умолчанию выключено: live не включается сам

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
        // ��� Confirm ���� �� ������ �� �������� ����
        viewModel.selectPrice(50000.0)
        assertNull(viewModel.selectedPrice.first())

        // � Confirm: ������ ���� ��������, ��������� � �������
        viewModel.setConfirmOrders(true)
        viewModel.selectPrice(50000.0)
        assertEquals(50000.0, viewModel.selectedPrice.first())
        viewModel.selectPrice(50000.0)
        assertNull(viewModel.selectedPrice.first())

        // ���������� Confirm ���������� ���������
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
        viewModel.setTradingEnabled(true)
        val intent = OrderIntent.MarketBuy("BTCUSDT", 0.5)
        viewModel.handleOrderIntent(intent)
        advanceUntilIdle()
        // ������� ����������� ���������� ����� ���� � ������ ����������, ��� �� ������
    }

    @Test
    fun `handleOrderIntent ToggleTrading toggles trading`() = testScope.runTest {
        val intent = OrderIntent.ToggleTrading
        viewModel.handleOrderIntent(intent)
        advanceUntilIdle()
        assertTrue(viewModel.isTradingEnabled.first())

        viewModel.handleOrderIntent(intent)
        advanceUntilIdle()
        assertFalse(viewModel.isTradingEnabled.first())
    }

    @Test
    fun `paper toggle turns trading off`() = testScope.runTest {
        viewModel.setTradingEnabled(true)
        viewModel.setPaperEnabled(true)
        assertFalse(viewModel.isTradingEnabled.first())

        viewModel.setTradingEnabled(true)
        viewModel.setPaperEnabled(false)
        assertFalse(viewModel.isTradingEnabled.first())
    }

    @Test
    fun `provider change turns trading off`() = testScope.runTest {
        viewModel.setTradingEnabled(true)
        val newOptions = DomOptions.default().copy(provider = "mexc-nous-0.0.1")
        viewModel.updateDomOptions(newOptions)
        advanceUntilIdle()
        assertFalse(viewModel.isTradingEnabled.first())
    }

    @Test
    fun `book window stores window data`() = testScope.runTest {
        runCurrent() // ��� �������� (callbackFlow + ��������) ���������
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

        // ����� ���� ��� ������ 49900 � ������� �������� �� �����
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
        // ��������� ������ � � ������� ��������� (BaseTick: 1 ��� = 1.0)
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
        // ask-������� �� 50100: ������ askSteps
        assertNull(levels[0].bidSteps)
        assertEquals(300L, levels[0].askSteps)
        // bid-������� �� 50000: ������ bidSteps
        assertEquals(100L, levels[1].bidSteps)
        assertNull(levels[1].askSteps)
        // bid-������� �� 49900
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

        // 50001, 50005, 50009 > ���� ������� 50000; 50010 > 50010
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

        // 30 bid-������� � ���� ����������� ������� (��� �������: ������� ���������� �� ����)
        val bids = (0 until 30).map { i -> PriceUpdate((50000.0 - i), 1.0) }
        fakeDomAdapter.windowFlow.emit(BookWindowLevels(bids = bids, asks = emptyList()))
        advanceUntilIdle()

        assertEquals(30, viewModel.sortedLevels.size)
        assertEquals(50000L, viewModel.sortedLevels.first().priceTicks)
        assertEquals(49971L, viewModel.sortedLevels.last().priceTicks)
    }

    @Test
    fun `window before metadata - book is drawn after metadata arrives`() = testScope.runTest {
        // ���� �������� �� ���������� (fetch �������� �� 500��) � ������� ��� ���.
        // runCurrent ������������ ������ ������ � ������� ����������� ������� (t=0),
        // �� ��������� delay(500) � ���� �������� ������ ������
        runCurrent() // ��� �������� (callbackFlow + ��������) ��������� ��� ����������� �������
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

        // �������� ���������� (tickSize=1.0, stepSize=0.01) � ����� �������� �� ���������� ����
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

    @Test
    fun `stale symbol metadata does not overwrite qty of the new symbol`() = testScope.runTest {
        fakeSymbolInfoAdapter.tickSize = 0.01
        // AAAUSDT отвечает медленно; его minQty на момент запроса — 0.001
        fakeSymbolInfoAdapter.delayBySymbol["AAAUSDT"] = 1_000

        viewModel.updateDomOptions(
            DomOptions.default().copy(symbol = TradingSymbol("AAAUSDT", "AAA/USDT", "binance-nous-0.0.1"))
        )
        runCurrent()

        // Пока метаданные AAA ещё грузятся — переключаемся на BBB (min 0.5)
        fakeSymbolInfoAdapter.minQty = 0.5
        viewModel.updateDomOptions(
            viewModel.domOptions.value.copy(symbol = TradingSymbol("BBBUSDT", "BBB/USDT", "binance-nous-0.0.1"))
        )
        advanceUntilIdle()

        // Устаревший ответ AAA не должен перетереть qty/placeholder нового символа
        assertEquals("0.5", viewModel.orderQuantity.first())
        assertEquals("0.5", viewModel.symbolMinQty.first())
    }

    @Test
    fun `order panel settings persist per panel and restore`() = testScope.runTest {
        val store = FakeStateStore()
        val vm = DomViewModel(
            providerRegistry = ProviderRegistry(listOf(fakeProvider)),
            coroutineDispatcher = testDispatcher,
            stateStore = store,
        )
        vm.attachPanel("panel-1")
        advanceUntilIdle()

        vm.setConfirmOrders(true)
        vm.setReduceOnly(true)
        vm.setLimitOrderType(OrderType.IOC)
        vm.setLeverage(10)
        vm.setMarginMode(1)
        vm.updateOrderQuantity("2.5")
        vm.updateDomOptions(vm.domOptions.value.copy(collapsed = true))
        advanceUntilIdle()

        // Новый экземпляр панели (рестарт) читает сохранённые настройки
        val restored = DomViewModel(
            providerRegistry = ProviderRegistry(listOf(fakeProvider)),
            coroutineDispatcher = testDispatcher,
            stateStore = store,
        )
        restored.attachPanel("panel-1")
        advanceUntilIdle()

        assertTrue(restored.confirmOrders.first())
        assertTrue(restored.reduceOnly.first())
        assertEquals(OrderType.IOC, restored.limitOrderType.first())
        assertEquals(10, restored.leverage.first())
        assertEquals(1, restored.marginMode.first())
        assertEquals("2.5", restored.orderQuantity.first())
        assertTrue(restored.domOptions.value.collapsed)
    }
}

/** Простейший StateStore в памяти для тестов персиста. */
class FakeStateStore : StateStore {
    val data = mutableMapOf<String, String>()
    override suspend fun getString(key: String): String? = data[key]
    override suspend fun putString(key: String, value: String) {
        data[key] = value
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
    var minQty: Double = 0.001

    /** Задержка ответа по символу — для тестов гонки метаданных. */
    val delayBySymbol = mutableMapOf<String, Long>()

    override suspend fun getSymbolInfo(symbol: String): SymbolInfo? {
        // Значения фиксируем на момент запроса (как реальный REST-ответ)
        val minAtRequest = minQty
        delayBySymbol[symbol]?.let { kotlinx.coroutines.delay(it) }
        return tickSize?.let {
            SymbolInfo(
                symbol = symbol,
                tickSize = it,
                stepSize = 0.01,
                minQty = minAtRequest,
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
