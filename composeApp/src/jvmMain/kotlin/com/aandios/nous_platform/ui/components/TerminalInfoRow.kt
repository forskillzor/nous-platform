/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aandios.nous.core.ui.component.TerminalInfoRow as SharedTerminalInfoRow

@Composable
fun TerminalInfoRow(
    label: String,
    value: String,
    isPositive: Boolean? = null,
    modifier: Modifier = Modifier
) = SharedTerminalInfoRow(label, value, isPositive, modifier)
