/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// features/feature-dom/src/commonMain/kotlin/com/aandios/nous/feature/dom/di/FeatureDomModule.kt
package com.aandios.nous.feature.dom.di

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.di.coreModule
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

    // 1. Провайдер Binance (превью живут на одном провайдере) + реестр
    single<Provider> {
        BinanceProviderFactory().createProvider(
            config = ProviderConfig(displayName = "Binance"),
            networkManager = get<NetworkManager>(),
        )
    }
    single<ProviderRegistry> { ProviderRegistry(getAll<Provider>()) }

    // 2. ViewModel — адаптеры резолвятся из реестра по выбранному провайдеру
    factory {
        DomViewModel(providerRegistry = get())
    }
}
