/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun TerminalSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            // Активный: серый кружок на терминальном зелёном треке
            checkedThumbColor = MaterialTheme.colorScheme.outlineVariant,
            checkedTrackColor = Color(0xFF00C853).copy(alpha = 0.65f),
            // Неактивный — как раньше (серый)
            uncheckedThumbColor = MaterialTheme.colorScheme.outlineVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    )
}
