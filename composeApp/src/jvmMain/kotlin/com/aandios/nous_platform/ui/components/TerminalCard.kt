/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aandios.nous.core.ui.component.TerminalCard as SharedTerminalCard

@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) = SharedTerminalCard(modifier = modifier, content = content)
