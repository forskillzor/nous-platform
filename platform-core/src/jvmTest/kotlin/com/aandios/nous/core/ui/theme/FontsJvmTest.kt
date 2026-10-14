/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.theme

import kotlin.test.Test
import kotlin.test.assertNotNull

class FontsJvmTest {
    @Test
    fun bundledJetBrainsMonoFontsAreOnTheRuntimeClasspath() {
        val classLoader = Thread.currentThread().contextClassLoader

        listOf(
            "fonts/JetBrainsMono-Regular.ttf",
            "fonts/JetBrainsMono-Medium.ttf",
            "fonts/JetBrainsMono-Bold.ttf",
        ).forEach { path ->
            assertNotNull(classLoader.getResource(path), "Missing bundled font resource: $path")
        }
    }
}
