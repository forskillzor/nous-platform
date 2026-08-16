/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace.viewmodel

import androidx.compose.runtime.Composable
import com.aandios.nous.api.market.Provider
import com.aandios.nous.core.workspace.PanelConfig

class PanelViewModel(
    val config: PanelConfig,
    val provider: Provider,
    /** Composable lambda that renders this panel's actual content */
    val content: @Composable () -> Unit
)
