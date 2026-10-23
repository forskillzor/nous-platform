/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.IntSize

/**
 * Размер текущей workspace-панели в пикселях (ставится LayoutRenderer'ом на
 * листе). [IntSize.Zero] вне панели (standalone-окна, попапы вне workspace).
 */
val LocalPanelSize = compositionLocalOf { IntSize.Zero }
