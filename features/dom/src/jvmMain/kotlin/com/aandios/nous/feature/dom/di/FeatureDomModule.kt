/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// features/feature-dom/src/commonMain/kotlin/com/aandios/nous/feature/dom/di/FeatureDomModule.kt
package com.aandios.nous.feature.dom.di

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.core.data.repository.BookTickerRepositoryImpl
import com.aandios.nous.core.data.repository.SymbolInfoRepositoryImpl
import com.aandios.nous.core.di.coreModule
import com.aandios.nous.core.domain.repository.BookTickerRepository
import com.aandios.nous.core.domain.repository.DomRepository
import com.aandios.nous.core.domain.repository.SymbolInfoRepository
import com.aandios.nous.feature.dom.data.repository.DomRepositoryImpl
import com.aandios.nous.feature.dom.ui.DomViewModel
import com.aandios.nous.provider.binance.BinanceProviderFactory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Инициализация Koin ТОЛЬКО для превью
 * - Останавливаем старый контекст
 * - Не включаем binanceProviderModule (чтобы не было конфликта)
 * - Создаём Provider вручную через фабрику
 */
fun initKoinForPreview() {
    stopKoin() // ← Обязательно! Очищаем старый контекст
    startKoin {
        modules(
            coreModule,        // NetworkManager
            featureDomModule,   // Наш модуль с фичей DOM
        )
    }
}

val featureDomModule = module {

    // 1. Конфигурация для превью (тестовая сеть, без ключей)
    single<ProviderConfig> {
        ProviderConfig(
            apiKey = null,
            secretKey = null,
            isTestnet = false,  // ← Тестнет для безопасности
            customSettings = emptyMap()
        )
    }

    // 2. Создаём Provider НАПРЯМУЮ через фабрику
    // Передаём ВСЕ зависимости явно — никаких параметров в factory {}
    single<Provider> {
        val config = get<ProviderConfig>()
        val networkManager = get<NetworkManager>()

        // ← Используем фабрику напрямую, передаём всё что нужно
        BinanceProviderFactory().createProvider(
            config = config,
            networkManager = networkManager
        )
    }

    // 3. Адаптеры из провайдера
    single<DomAdapter> {
        get<Provider>().dom ?: error("DOM adapter not available")
    }
    single<BookTickerAdapter> {
        get<Provider>().bookTicker ?: error("BookTicker adapter not available")
    }
    single<SymbolInfoAdapter> {
        get<Provider>().symbolInfo ?: error("SymbolInfo adapter not available")
    }

    // 4. Репозитории
    single<DomRepository> {
        DomRepositoryImpl(domAdapter = get(), bookTickerAdapter = get())
    }
    single<BookTickerRepository> {
        BookTickerRepositoryImpl(bookTicker = get())
    }
    single<SymbolInfoRepository> {
        SymbolInfoRepositoryImpl(symbolInfoAdapter = get())
    }

    // 5. ViewModel — обе зависимости через get()
    factory {
        DomViewModel(
            domRepository = get(),
            symbolInfoRepository = get()
        )
    }
}