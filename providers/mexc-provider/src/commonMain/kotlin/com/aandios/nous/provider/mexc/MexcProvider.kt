/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

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
import com.aandios.nous.provider.mexc.adapter.MexcBookTickerAdapter
import com.aandios.nous.provider.mexc.adapter.MexcChartAdapter
import com.aandios.nous.provider.mexc.adapter.MexcDomAdapter
import com.aandios.nous.provider.mexc.adapter.MexcSymbolInfoAdapter
import com.aandios.nous.provider.mexc.adapter.MexcTradesAdapter
import com.aandios.nous.provider.mexc.adapter.MexcTradingAdapter
import io.ktor.client.HttpClient

/**
 * Провайдер MEXC Futures (USDT-M Perpetual, Contract API):
 * покрытие 1-в-1 как у binance-provider, кроме ликвидаций — у MEXC нет
 * публичного force-order-потока, поэтому [liquidation] = null.
 */
class MexcProvider(
    override val providerId: String,
    override val providerName: String,
    override val version: String,
    override val config: ProviderConfig,
    @Suppress("unused") override val networkManager: NetworkManager, // не используем его httpClient
) : Provider {

    // Отдельный HttpClient провайдера: единые таймауты/ContentNegotiation для MEXC
    private val mexcHttpClient: HttpClient = MexcHttpClientFactory.create()

    // Ключи для приватных endpoints: ProviderConfig → env (MEXC_API_KEY/MEXC_SECRET_KEY)
    private val privateApiKey: String get() = config.apiKey ?: getEnv("MEXC_API_KEY").orEmpty()
    private val privateSecretKey: String get() = config.secretKey ?: getEnv("MEXC_SECRET_KEY").orEmpty()

    // Общие гейты: один на провайдер — все адаптеры ходят через них,
    // чтобы не пробивать rate limit MEXC при открытии workspace с N панелями.
    private val restGate: MexcRestGate = MexcRestGate()
    private val streamHub: MexcStreamHub = MexcStreamHub(
        client = mexcHttpClient,
        config = config,
        privateApiKey = privateApiKey,
        privateSecretKey = privateSecretKey,
    )

    override val trades: TradesAdapter by lazy { MexcTradesAdapter(mexcHttpClient, config, restGate, streamHub) }
    override val dom: DomAdapter by lazy { MexcDomAdapter(mexcHttpClient, config, restGate, streamHub) }
    override val bookTicker: BookTickerAdapter by lazy { MexcBookTickerAdapter(mexcHttpClient, config, restGate, streamHub) }
    override val chart: ChartAdapter by lazy { MexcChartAdapter(mexcHttpClient, config, restGate, streamHub) }
    override val trading: TradingAdapter by lazy { MexcTradingAdapter(mexcHttpClient, config, restGate, streamHub) }
    override val symbolInfo: SymbolInfoAdapter by lazy { MexcSymbolInfoAdapter(mexcHttpClient, config, restGate) }

    // У MEXC нет публичного потока/эндпоинта ликвидаций
    override val liquidation: LiquidationAdapter? = null
}
