/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SymbolTab { ALL, FAVORITES, MD }

/**
 * Унифицированный дропдаун выбора символа с поиском и табами.
 * Переиспользуется в chart, trades, dom.
 *
 * @param symbols полный список символов
 * @param currentSymbol текущий выбранный символ
 * @param onSymbolSelected callback при выборе
 * @param favorites сет избранных символов
 * @param onToggleFavorite callback для добавления/удаления из избранного
 * @param symbolWithFootprint сет символов с агрегатами на market-data-server
 * @param modifier модификатор
 */
@Composable
fun SymbolSearchDropdown(
    symbols: List<String>,
    currentSymbol: String,
    onSymbolSelected: (String) -> Unit,
    favorites: Set<String> = emptySet(),
    onToggleFavorite: ((String) -> Unit)? = null,
    symbolsWithFootprint: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(SymbolTab.ALL) }

    if (!expanded) { searchQuery = "" }

    val sortedSymbols = remember(symbols, activeTab, favorites, symbolsWithFootprint) {
        val base = when (activeTab) {
            SymbolTab.ALL -> symbols
            SymbolTab.FAVORITES -> symbols.filter { it in favorites }
            SymbolTab.MD -> symbols.filter { it in symbolsWithFootprint }
        }
        base.sortedWith(compareBy({ it !in favorites }, { it }))
    }

    val filteredSymbols = remember(sortedSymbols, searchQuery) {
        if (searchQuery.isBlank()) sortedSymbols
        else sortedSymbols.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val dropdownContent: @Composable () -> Unit = {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = true }
            ) {
                Text(
                    text = currentSymbol,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(0.dp, 4.dp),
                modifier = Modifier.widthIn(min = 200.dp, max = 280.dp)
            ) {
                // Tabs
                if (symbolsWithFootprint.isNotEmpty() || favorites.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TabChip("All", SymbolTab.ALL, activeTab) { activeTab = it }
                        TabChip("Fav", SymbolTab.FAVORITES, activeTab) { activeTab = it }
                        if (symbolsWithFootprint.isNotEmpty()) {
                            TabChip("md", SymbolTab.MD, activeTab) { activeTab = it }
                        }
                    }
                }

                // Search
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) Text("Search...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        inner()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // List
                Column(
                    modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                ) {
                    filteredSymbols.forEach { sym ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = sym,
                                        color = if (sym == currentSymbol) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    if (sym in symbolsWithFootprint) {
                                        Spacer(Modifier.width(5.dp))
                                        Text("md", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                    if (sym in favorites) {
                                        Spacer(Modifier.width(5.dp))
                                        Text("★", color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 10.sp)
                                    }
                                }
                            },
                            onClick = {
                                onSymbolSelected(sym)
                                expanded = false
                            },
                            modifier = Modifier.background(if (sym == currentSymbol) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                        )
                    }
                    if (filteredSymbols.isEmpty()) {
                        Text("No symbols", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }

    if (showLabel) {
        TerminalDropdownWithLabel(label = "Sym", modifier = modifier) {
            dropdownContent()
        }
    } else {
        TerminalDropdownWithLabel(label = "", modifier = modifier) {
            dropdownContent()
        }
    }
}

@Composable
private fun TabChip(label: String, tab: SymbolTab, active: SymbolTab, onSelect: (SymbolTab) -> Unit) {
    val isActive = tab == active
    Text(
        text = label,
        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clickable { onSelect(tab) }
            .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp)
    )
}
