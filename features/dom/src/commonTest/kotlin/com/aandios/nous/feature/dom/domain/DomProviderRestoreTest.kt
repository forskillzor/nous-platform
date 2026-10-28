/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DomProviderRestoreTest {

    private val default = "binance-nous-0.0.1"

    @Test
    fun `stored provider is used when config has none`() {
        assertEquals(
            "bybit-nous-0.0.1",
            resolveRestoredProvider(
                preferred = null,
                stored = "bybit-nous-0.0.1",
                defaultProvider = default,
                firstAttach = true,
            ),
        )
    }

    @Test
    fun `config provider is used when panel store is empty`() {
        assertEquals(
            "bybit-nous-0.0.1",
            resolveRestoredProvider(
                preferred = "bybit-nous-0.0.1",
                stored = null,
                defaultProvider = default,
                firstAttach = true,
            ),
        )
    }

    @Test
    fun `matching providers keep the config value`() {
        assertEquals(
            "bybit-nous-0.0.1",
            resolveRestoredProvider(
                preferred = "bybit-nous-0.0.1",
                stored = "bybit-nous-0.0.1",
                defaultProvider = default,
                firstAttach = true,
            ),
        )
    }

    @Test
    fun `stale default config loses to a saved panel provider on first attach`() {
        assertEquals(
            "bybit-nous-0.0.1",
            resolveRestoredProvider(
                preferred = default,
                stored = "bybit-nous-0.0.1",
                defaultProvider = default,
                firstAttach = true,
            ),
        )
    }

    @Test
    fun `explicit non-default config wins on first attach`() {
        assertEquals(
            "mexc-nous-0.0.1",
            resolveRestoredProvider(
                preferred = "mexc-nous-0.0.1",
                stored = "bybit-nous-0.0.1",
                defaultProvider = default,
                firstAttach = true,
            ),
        )
    }

    @Test
    fun `after first attach runtime config wins even for the default provider`() {
        assertEquals(
            default,
            resolveRestoredProvider(
                preferred = default,
                stored = "bybit-nous-0.0.1",
                defaultProvider = default,
                firstAttach = false,
            ),
        )
    }
}
