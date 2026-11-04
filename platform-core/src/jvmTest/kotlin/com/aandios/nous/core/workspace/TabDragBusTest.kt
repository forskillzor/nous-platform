/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabDragBusTest {

    @Test
    fun `ending a drag only clears its matching payload`() {
        val bus = TabDragBus()
        val first = TabDragBus.Payload("window-1", "workspace-1")
        val second = TabDragBus.Payload("window-2", "workspace-2")
        bus.start(first)

        assertFalse(bus.end(second))
        assertTrue(bus.active.value == first)

        bus.markCancelled(first)
        assertTrue(bus.wasCancelled(first))
        assertTrue(bus.end(first))
        assertNull(bus.active.value)
        assertFalse(bus.wasCancelled(first))
    }
}
