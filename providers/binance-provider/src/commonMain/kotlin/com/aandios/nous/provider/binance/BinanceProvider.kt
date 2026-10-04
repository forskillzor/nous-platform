/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.*
import com.aandios.nous.provider.binance.adapter.BinanceBookTickerAdapter
import com.aandios.nous.provider.binance.adapter.BinanceChartAdapter
import com.aandios.nous.provider.binance.adapter.BinanceDomAdapter
import com.aandios.nous.provider.binance.adapter.BinanceLiquidationAdapter
import com.aandios.nous.provider.binance.adapter.BinanceSymbolInfoAdapter
import com.aandios.nous.provider.binance.adapter.BinanceTradesAdapter
import com.aandios.nous.provider.binance.adapter.BinanceTradingAdapter
import io.ktor.client.HttpClient

class BinanceProvider(
    override val providerId: String,
    override val providerName: String,
    override val version: String,
    override val config: ProviderConfig,
    override val networkManager: NetworkManager,  // Оставляем для совместимости, но не используем его httpClient
) : Provider {

    // Создаём отдельный HttpClient для Binance с правильным classDiscriminator
    private val binanceHttpClient: HttpClient = BinanceHttpClientFactory.create()

    // Общие гейты: один на провайдер — все адаптеры ходят через них,
    // чтобы не пробивать rate limit Binance при открытии workspace с N панелями.
    private val restGate: BinanceRestGate = BinanceRestGate()
    private val streamHub: BinanceStreamHub = BinanceStreamHub(binanceHttpClient, config)

    override val trades by lazy { BinanceTradesAdapter(binanceHttpClient, config, streamHub) }
    override val dom: DomAdapter by lazy { BinanceDomAdapter(binanceHttpClient, config, streamHub) }
    override val bookTicker: BookTickerAdapter by lazy { BinanceBookTickerAdapter(binanceHttpClient, config, restGate, streamHub) }
    override val chart: ChartAdapter by lazy { BinanceChartAdapter(binanceHttpClient, config, restGate, streamHub) }
    override val trading: TradingAdapter by lazy { BinanceTradingAdapter(binanceHttpClient, config) }
    override val symbolInfo: SymbolInfoAdapter by lazy { BinanceSymbolInfoAdapter(binanceHttpClient, config, restGate) }
    override val liquidation: LiquidationAdapter by lazy { BinanceLiquidationAdapter(binanceHttpClient, config, restGate, streamHub) }

}