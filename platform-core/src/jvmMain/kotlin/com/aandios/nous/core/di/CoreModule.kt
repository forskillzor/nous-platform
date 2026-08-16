/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.di

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.core.network.NetworkManagerImpl
import org.koin.dsl.module

val coreModule = module {
    single<NetworkManager> { NetworkManagerImpl() }
    single { get<NetworkManager>().httpClient }
}