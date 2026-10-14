/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.core.ui.theme.TerminalThemes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ThemeSettingsControllerTest {
    @Test
    fun restoresSavedTheme() = runBlocking {
        val controller = ThemeSettingsController(MemoryStateStore("Night"))

        controller.start()
        val state = controller.state.first { !it.loading }

        assertEquals(TerminalThemes.Night, state.theme)
        assertEquals(null, state.error)
    }

    @Test
    fun persistsSelectedThemeBeforeApplyingIt() = runBlocking {
        val store = MemoryStateStore()
        val controller = ThemeSettingsController(store)
        controller.start()
        controller.state.first { !it.loading }

        controller.select(TerminalThemes.Night)
        val state = controller.state.first { it.theme == TerminalThemes.Night && !it.saving }

        assertEquals("Night", store.value)
        assertFalse(state.loading)
        assertEquals(null, state.error)
    }

    @Test
    fun reportsUnknownSavedTheme() = runBlocking {
        val controller = ThemeSettingsController(MemoryStateStore("Unavailable"))

        controller.start()
        val state = controller.state.first { !it.loading }

        assertEquals(TerminalThemes.Dark, state.theme)
        assertNotNull(state.error)
        Unit
    }

    private class MemoryStateStore(var value: String? = null) : StateStore {
        override suspend fun getString(key: String): String? = value

        override suspend fun putString(key: String, value: String) {
            this.value = value
        }
    }
}
