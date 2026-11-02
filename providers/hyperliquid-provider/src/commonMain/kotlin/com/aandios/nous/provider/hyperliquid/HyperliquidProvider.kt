/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.adapters.TradingAdapter
import com.aandios.nous.provider.hyperliquid.adapter.HyperliquidBookTickerAdapter
import com.aandios.nous.provider.hyperliquid.adapter.HyperliquidChartAdapter
import com.aandios.nous.provider.hyperliquid.adapter.HyperliquidDomAdapter
import com.aandios.nous.provider.hyperliquid.adapter.HyperliquidSymbolInfoAdapter
import com.aandios.nous.provider.hyperliquid.adapter.HyperliquidTradesAdapter
import com.aandios.nous.provider.hyperliquid.adapter.HyperliquidTradingAdapter

class HyperliquidProvider(
    override val providerId: String,
    override val providerName: String,
    override val version: String,
    override val config: ProviderConfig,
    override val networkManager: NetworkManager,
) : Provider {
    private val info: HyperliquidInfoApi = HyperliquidInfoClient(
        client = networkManager.httpClient,
        baseUrl = hyperliquidRestBase(config.isTestnet),
    )
    private val marketData = HyperliquidMarketData(info, config)
    private val streams = HyperliquidStreamHub(
        client = networkManager.httpClient,
        wsUrl = hyperliquidWsUrl(config.isTestnet),
    )

    override val trades: TradesAdapter by lazy { HyperliquidTradesAdapter(info, streams) }
    override val dom: DomAdapter by lazy { HyperliquidDomAdapter(streams) }
    override val bookTicker: BookTickerAdapter by lazy {
        HyperliquidBookTickerAdapter(info, streams, marketData)
    }
    override val chart: ChartAdapter by lazy {
        HyperliquidChartAdapter(info, streams, marketData)
    }
    override val symbolInfo: SymbolInfoAdapter by lazy { HyperliquidSymbolInfoAdapter(marketData) }

    // Hyperliquid has no public all-liquidations stream.
    override val liquidation: LiquidationAdapter? = null

    private val credentials = resolveHyperliquidCredentials(config)
    private val signer = HyperliquidL1Signer(
        privateKeyHex = credentials.privateKeyHex,
        configuredAccountAddress = credentials.accountAddress,
        configuredVaultAddress = credentials.vaultAddress,
        isMainnet = !config.isTestnet,
    )

    override val trading: TradingAdapter by lazy {
        HyperliquidTradingAdapter(
            info = info,
            streams = streams,
            marketData = marketData,
            signer = signer,
            config = config,
        )
    }

    override suspend fun stop() {
        streams.close()
    }
}
