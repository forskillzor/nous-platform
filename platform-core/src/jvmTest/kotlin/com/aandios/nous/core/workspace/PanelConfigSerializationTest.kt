/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PanelConfigSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `legacy panel json without provider fields decodes with nulls`() {
        val panel = PanelConfig(
            id = "p1",
            type = PanelType.CHART,
            providerRef = "main",
            symbol = "BTCUSDT",
            state = PanelState.Chart(),
        )
        val encoded = json.parseToJsonElement(json.encodeToString(panel)).jsonObject
        val legacy = JsonObject(encoded - "providerId" - "providerName")

        val decoded = json.decodeFromString<PanelConfig>(legacy.toString())

        assertEquals("p1", decoded.id)
        assertEquals("BTCUSDT", decoded.symbol)
        assertNull(decoded.providerId)
        assertNull(decoded.providerName)
    }

    @Test
    fun `panel provider roundtrips`() {
        val panel = PanelConfig(
            id = "p1",
            type = PanelType.CHART,
            providerRef = "main",
            symbol = "SOLUSDT",
            providerId = "bybit-nous-0.0.1",
            providerName = "Bybit",
        )

        val decoded = json.decodeFromString<PanelConfig>(json.encodeToString(panel))

        assertEquals("bybit-nous-0.0.1", decoded.providerId)
        assertEquals("Bybit", decoded.providerName)
    }
}
