/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aandios.nous.core.ui.component.TerminalBadge as SharedTerminalBadge

@Composable
fun TerminalBadge(
    text: String,
    isBullish: Boolean? = null,
    modifier: Modifier = Modifier
) = SharedTerminalBadge(text, isBullish, modifier)
