/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.*
import com.aandios.nous.provider.bybit.adapter.BybitBookTickerAdapter
import com.aandios.nous.provider.bybit.adapter.BybitChartAdapter
import com.aandios.nous.provider.bybit.adapter.BybitDomAdapter
import com.aandios.nous.provider.bybit.adapter.BybitLiquidationAdapter
import com.aandios.nous.provider.bybit.adapter.BybitSymbolInfoAdapter
import com.aandios.nous.provider.bybit.adapter.BybitTradesAdapter
import com.aandios.nous.provider.bybit.adapter.BybitTradingAdapter

class BybitProvider(
    override val providerId: String,
    override val providerName: String,
    override val version: String,
    override val config: ProviderConfig,
    override val networkManager: NetworkManager,
) : Provider {
    private val client = buildBybitHttpClient()
    private val rest = BybitRestClient(client, config)
    private val apiKey = config.apiKey ?: bybitEnvironmentVariable("BYBIT_API_KEY").orEmpty()
    private val apiSecret = config.secretKey ?: bybitEnvironmentVariable("BYBIT_SECRET_KEY").orEmpty()
    private val streams = BybitStreamHub(client, config)
    private val privateStreams = BybitPrivateStreamHub(client, config, apiKey, apiSecret)

    override val trades: TradesAdapter by lazy { BybitTradesAdapter(rest, streams) }
    override val dom: DomAdapter by lazy { BybitDomAdapter(streams) }
    override val bookTicker: BookTickerAdapter by lazy { BybitBookTickerAdapter(rest, streams) }
    override val chart: ChartAdapter by lazy { BybitChartAdapter(rest, streams) }
    override val symbolInfo: SymbolInfoAdapter by lazy { BybitSymbolInfoAdapter(rest) }
    override val liquidation: LiquidationAdapter by lazy { BybitLiquidationAdapter(streams) }

    override val trading: TradingAdapter by lazy {
        BybitTradingAdapter(client, privateStreams, config, apiKey, apiSecret)
    }
}
