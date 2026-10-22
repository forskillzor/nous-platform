/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IndicatorColorPickerTest {

    @Test
    fun `hex colors parse as opaque rgb or explicit argb`() {
        assertEquals(0xFFFF0000L, parseIndicatorColorArgb("#ff0000"))
        assertEquals(0xFF00FF00L, parseIndicatorColorArgb("00FF00"))
        assertEquals(0x80FF0000L, parseIndicatorColorArgb("80FF0000"))
    }

    @Test
    fun `invalid hex colors are rejected`() {
        assertNull(parseIndicatorColorArgb(""))
        assertNull(parseIndicatorColorArgb("#FFF"))
        assertNull(parseIndicatorColorArgb("#GGGGGG"))
    }

    @Test
    fun `argb formats as an uppercase rgb hex string`() {
        assertEquals("#FF0000", indicatorColorHex(0xFFFF0000L))
        assertEquals("#00FF00", indicatorColorHex(0x8000FF00L))
    }
}
