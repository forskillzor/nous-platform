/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.feature.trades.ui.SizeFilter

@Composable
fun SizeFilterDropdown(
    currentFilter: SizeFilter,
    minQty: Double?,
    customPresets: List<Double>,
    onFilterChanged: (SizeFilter) -> Unit,
    onPresetAdd: (Double) -> Unit,
    onPresetEdit: (Int, Double) -> Unit,
    onPresetDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }

    val displayLabel = when (currentFilter) {
        is SizeFilter.All -> "All"
        is SizeFilter.MinQty -> if (minQty != null) "≥ ${fmt(minQty)}" else "≥ min"
        is SizeFilter.MinQtyx10 -> if (minQty != null) "≥ ${fmt(minQty * 10)}" else "≥ ×10"
        is SizeFilter.MinQtyx100 -> if (minQty != null) "≥ ${fmt(minQty * 100)}" else "≥ ×100"
        is SizeFilter.Custom -> "≥ ${currentFilter.value.toString().trimEnd('0').trimEnd('.')}"
    }

    Box(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = displayLabel,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Filter",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(200.dp)
        ) {
            // "All" option
            DropdownMenuItem(
                text = { Text("All", fontSize = 12.sp) },
                onClick = {
                    onFilterChanged(SizeFilter.All)
                    customText = ""
                    expanded = false
                },
                leadingIcon = {
                    if (currentFilter is SizeFilter.All)
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    else Spacer(Modifier.size(14.dp))
                }
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Manual input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = 12.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).height(28.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (customText.isEmpty()) {
                                Text(
                                    "≥ custom…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Spacer(Modifier.width(6.dp))
                TextButton(
                    onClick = {
                        val v = customText.toDoubleOrNull()
                        if (v != null && v > 0) {
                            onFilterChanged(SizeFilter.Custom(v))
                            expanded = false
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text("Apply", fontSize = 11.sp)
                }
            }

            // Saved presets
            if (customPresets.isNotEmpty() || minQty != null) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Quick min-based presets
                if (minQty != null) {
                    PresetRow("≥ ${fmt(minQty)}", currentFilter is SizeFilter.MinQty) {
                        onFilterChanged(SizeFilter.MinQty); expanded = false
                    }
                    PresetRow("≥ ${fmt(minQty * 10)}", currentFilter is SizeFilter.MinQtyx10) {
                        onFilterChanged(SizeFilter.MinQtyx10); expanded = false
                    }
                    PresetRow("≥ ${fmt(minQty * 100)}", currentFilter is SizeFilter.MinQtyx100) {
                        onFilterChanged(SizeFilter.MinQtyx100); expanded = false
                    }
                }

                // User-saved presets
                if (customPresets.isNotEmpty()) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    customPresets.forEachIndexed { index, value ->
                        EditablePresetRow(value, index, onPresetEdit, onPresetDelete, onFilterChanged) { expanded = false }
                    }
                }
            }

            // Add preset button
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            DropdownMenuItem(
                text = { Text("+ Add preset", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    val v = customText.toDoubleOrNull()
                    if (v != null && v > 0) {
                        onPresetAdd(v)
                        customText = ""
                    }
                },
            )
        }
    }
}

@Composable
private fun PresetRow(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isActive)
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        else Spacer(Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun EditablePresetRow(
    value: Double,
    index: Int,
    onPresetEdit: (Int, Double) -> Unit,
    onPresetDelete: (Int) -> Unit,
    onFilterChanged: (SizeFilter) -> Unit,
    closeMenu: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var editText by remember(value) { mutableStateOf(value.toString().trimEnd('0').trimEnd('.')) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!editing) { onFilterChanged(SizeFilter.Custom(value)); closeMenu() }
            }
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (editing) {
            BasicTextField(
                value = editText,
                onValueChange = { editText = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.inverseOnSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).height(28.dp),
            )
            IconButton(onClick = {
                val v = editText.toDoubleOrNull()
                if (v != null && v > 0) { onPresetEdit(index, v); editing = false }
            }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            Text(
                "≥ ${
                    value.toString().trimEnd('0').trimEnd('.')
                }",
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { editing = true; editText = value.toString().trimEnd('0').trimEnd('.') }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            IconButton(onClick = { onPresetDelete(index) }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}

private fun fmt(value: Double): String {
    val decimals = when {
        value >= 1000 -> 1
        value >= 1 -> 3
        value >= 0.001 -> 4
        else -> 6
    }
    val multiplier = listOf(1.0, 10.0, 100.0, 1000.0, 10000.0, 100000.0, 1000000.0)[decimals]
    val rounded = kotlin.math.round(value * multiplier) / multiplier
    val str = rounded.toString()
    val dot = str.indexOf('.')
    if (dot < 0) return if (decimals > 0) "$str.${"0".repeat(decimals)}" else str
    val dec = str.substring(dot + 1).padEnd(decimals, '0').take(decimals)
    return str.substring(0, dot) + "." + dec
}
