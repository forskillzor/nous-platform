/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.feature.chart.ui.chart.indicatorChartColors
import com.aandios.nous.core.ui.component.ExplorerStar
import com.aandios.nous.core.ui.component.ExplorerTab
import com.aandios.nous.core.ui.component.TerminalBoxDropdown
import com.aandios.nous.core.ui.component.TerminalBoxStyle
import com.aandios.nous.core.ui.component.TerminalDropdownMenu
import com.aandios.nous.core.ui.component.TerminalExplorerPopupContent
import com.aandios.nous.core.ui.component.TerminalMenuItem
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.research.indicator.IndicatorDefinition
import com.aandios.nous.research.indicator.IndicatorInstance
import com.aandios.nous.research.indicator.IndicatorPaneLayout
import com.aandios.nous.research.indicator.IndicatorParamSpec
import com.aandios.nous.research.indicator.IndicatorPlacement
import com.aandios.nous.research.indicator.IndicatorRegistry
import com.aandios.nous.research.indicator.IndicatorType
import com.aandios.nous.research.indicator.indicatorDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun ChartIndicatorButton(menu: @Composable (dismiss: () -> Unit) -> Unit) {
    val tokens = LocalTerminalDesignTokens.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                .clickable { expanded = true }
                .background(if (expanded) tokens.colors.selection else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "ƒx",
                color = if (expanded) tokens.colors.accent else tokens.colors.label,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
            )
        }
        TerminalDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 310.dp, max = 380.dp),
        ) {
            menu { expanded = false }
        }
    }
}

private enum class IndicatorMenuTab { ALL, FAVOURITES }

@Composable
fun ChartIndicatorMenu(
    chartViewModel: ChartViewModel,
    dismiss: () -> Unit,
    repository: IndicatorPresetRepository? = null,
) {
    val uiState by chartViewModel.state.collectAsState()
    val indicators = uiState.indicators
    val tokens = LocalTerminalDesignTokens.current
    val scope = rememberCoroutineScope()
    var editingId by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(IndicatorMenuTab.ALL) }
    var saveNameOpen by remember { mutableStateOf(false) }
    var renamingPresetId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    val libraryFlow = remember(repository) {
        repository?.state ?: MutableStateFlow(IndicatorLibrary())
    }
    val library by libraryFlow.collectAsState()
    val favourites = remember(library.favouriteTypes) { library.favouriteTypes.toSet() }

    LaunchedEffect(repository) { repository?.load() }

    fun toggleFavourite(type: IndicatorType) {
        repository?.let { repo ->
            scope.launch { repo.toggleFavourite(type) }
        }
    }

    TerminalExplorerPopupContent(
        tabs = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                ExplorerTab("ALL", active = tab == IndicatorMenuTab.ALL) {
                    tab = IndicatorMenuTab.ALL
                }
                ExplorerTab("FAVOURITES", active = tab == IndicatorMenuTab.FAVOURITES) {
                    tab = IndicatorMenuTab.FAVOURITES
                }
                repository?.let { repo ->
                    TerminalBoxDropdown(
                        text = "LISTS",
                        menuWidth = 230.dp,
                        style = TerminalBoxStyle.NORMAL,
                    ) { dropdownDismiss ->
                        IndicatorPresetListMenu(
                            presets = library.presets,
                            onApply = { preset ->
                                chartViewModel.dispatch(ChartIntent.ApplyIndicatorPreset(preset))
                                dropdownDismiss()
                                dismiss()
                            },
                            onRename = { preset ->
                                renamingPresetId = preset.id
                                saveNameOpen = false
                                dropdownDismiss()
                            },
                            onDelete = { preset ->
                                scope.launch { repo.deletePreset(preset.id) }
                            },
                            onSaveCurrent = {
                                saveNameOpen = true
                                renamingPresetId = null
                                dropdownDismiss()
                            },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        },
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = "Search indicators",
        searchFocusRequester = searchFocus,
        autoFocusSearch = true,
        dividerAfterTabs = false,
        header = {
            if (saveNameOpen) {
                IndicatorPresetNameRow(
                    initial = "",
                    onCommit = { name ->
                        repository?.let { repo ->
                            scope.launch { repo.savePreset(name, uiState.indicators) }
                        }
                        saveNameOpen = false
                    },
                    onCancel = { saveNameOpen = false },
                )
            }
            library.presets.firstOrNull { it.id == renamingPresetId }?.let { preset ->
                IndicatorPresetNameRow(
                    initial = preset.name,
                    onCommit = { name ->
                        repository?.let { repo ->
                            scope.launch { repo.renamePreset(preset.id, name) }
                        }
                        renamingPresetId = null
                    },
                    onCancel = { renamingPresetId = null },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.md, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                Text(
                    text = "PANE LAYOUT",
                    color = tokens.colors.label,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    fontFamily = TerminalFontFamily,
                    modifier = Modifier.weight(1f),
                )
                ExplorerTab("TABS", active = indicators.paneLayout == IndicatorPaneLayout.TABS) {
                    chartViewModel.dispatch(ChartIntent.SetIndicatorPaneLayout(IndicatorPaneLayout.TABS))
                }
                ExplorerTab("STACKED", active = indicators.paneLayout == IndicatorPaneLayout.STACKED) {
                    chartViewModel.dispatch(ChartIntent.SetIndicatorPaneLayout(IndicatorPaneLayout.STACKED))
                }
            }
        },
    ) {
        val allDefinitions = IndicatorRegistry.all.map { it.definition }
        val definitions = filterIndicatorDefinitions(
            definitions = when (tab) {
                IndicatorMenuTab.ALL -> allDefinitions
                IndicatorMenuTab.FAVOURITES -> allDefinitions.filter { it.type in favourites }
            },
            query = query,
        )
        val overlays = definitions.filter { it.placement == IndicatorPlacement.OVERLAY }
        val panes = definitions.filter { it.placement == IndicatorPlacement.PANE }

        if (definitions.isEmpty()) {
            IndicatorMenuHint(
                if (tab == IndicatorMenuTab.FAVOURITES) "No favourites" else "No indicators",
            )
        }
        if (overlays.isNotEmpty()) {
            IndicatorMenuSection("OVERLAYS")
            overlays.forEach { definition ->
                IndicatorDefinitionRow(
                    label = definition.label,
                    starred = definition.type in favourites,
                    onToggleFavourite = { toggleFavourite(definition.type) },
                    onAdd = {
                        chartViewModel.dispatch(ChartIntent.AddIndicatorOverlay(definition.type))
                    },
                )
            }
        }
        if (panes.isNotEmpty()) {
            if (overlays.isNotEmpty()) IndicatorMenuDivider()
            IndicatorMenuSection("BOTTOM PANE")
            panes.forEach { definition ->
                IndicatorDefinitionRow(
                    label = definition.label,
                    starred = definition.type in favourites,
                    onToggleFavourite = { toggleFavourite(definition.type) },
                    onAdd = {
                        chartViewModel.dispatch(ChartIntent.AddIndicatorToPane(definition.type))
                    },
                    trailing = {
                        Text(
                            text = "NEW TAB",
                            color = tokens.colors.label,
                            fontSize = 8.sp,
                            fontFamily = TerminalFontFamily,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .clickable {
                                    chartViewModel.dispatch(
                                        ChartIntent.AddIndicatorToPane(
                                            definition.type,
                                            createPane = true,
                                        ),
                                    )
                                }
                                .background(tokens.colors.selection)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    },
                )
            }
        }
        if (overlays.isNotEmpty() || panes.isNotEmpty()) IndicatorMenuDivider()

        IndicatorMenuSection("ON CHART")
        val instances = indicators.overlays + indicators.panes.flatMap { it.indicators }
        if (instances.isEmpty()) {
            Text(
                text = "No indicators",
                color = LocalTerminalDesignTokens.current.colors.label,
                fontSize = 9.sp,
                fontFamily = TerminalFontFamily,
                modifier = Modifier.padding(
                    start = LocalTerminalDesignTokens.current.spacing.md,
                    top = 2.dp,
                    bottom = 4.dp,
                ),
            )
        }
        instances.forEach { instance ->
            IndicatorInstanceRow(
                instance = instance,
                expanded = editingId == instance.id,
                onToggle = {
                    editingId = if (editingId == instance.id) null else instance.id
                },
                onRemove = {
                    chartViewModel.dispatch(ChartIntent.RemoveIndicator(instance.id))
                    if (editingId == instance.id) editingId = null
                },
                onParamChange = { key, value ->
                    chartViewModel.dispatch(
                        ChartIntent.UpdateIndicatorParams(instance.id, mapOf(key to value)),
                    )
                },
                onColorChange = { lineId, colorArgb ->
                    chartViewModel.dispatch(
                        ChartIntent.SetIndicatorLineColor(instance.id, lineId, colorArgb),
                    )
                },
            )
        }

        TerminalMenuItem(onClick = dismiss) {
            Text(
                text = "CLOSE",
                color = LocalTerminalDesignTokens.current.colors.label,
                fontSize = 9.sp,
                fontFamily = TerminalFontFamily,
            )
        }
    }
}

internal fun filterIndicatorDefinitions(
    definitions: List<IndicatorDefinition>,
    query: String,
): List<IndicatorDefinition> {
    val term = query.trim()
    if (term.isEmpty()) return definitions
    return definitions.filter {
        it.label.contains(term, ignoreCase = true) ||
            it.type.name.replace('_', ' ').contains(term, ignoreCase = true)
    }
}

@Composable
private fun IndicatorMenuSection(title: String) {
    val tokens = LocalTerminalDesignTokens.current
    Text(
        text = title,
        color = tokens.colors.label,
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = TerminalFontFamily,
        modifier = Modifier.padding(
            start = tokens.spacing.md,
            top = tokens.spacing.sm,
            bottom = 2.dp,
        ),
    )
}

@Composable
private fun IndicatorMenuDivider() {
    val tokens = LocalTerminalDesignTokens.current
    HorizontalDivider(
        color = tokens.colors.panelBorder,
        thickness = tokens.dimensions.borderWidth,
    )
}

@Composable
private fun IndicatorMenuHint(text: String) {
    val tokens = LocalTerminalDesignTokens.current
    Text(
        text = text,
        color = tokens.colors.label,
        fontSize = 9.sp,
        fontFamily = TerminalFontFamily,
        modifier = Modifier.padding(
            start = tokens.spacing.md,
            top = 2.dp,
            bottom = 4.dp,
        ),
    )
}

@Composable
private fun IndicatorDefinitionRow(
    label: String,
    starred: Boolean,
    onToggleFavourite: () -> Unit,
    onAdd: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalTerminalDesignTokens.current
    TerminalMenuItem(onClick = onAdd) {
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 20.dp)
                .clickable(onClick = onToggleFavourite),
            contentAlignment = Alignment.Center,
        ) {
            ExplorerStar(starred = starred)
        }
        Spacer(Modifier.width(tokens.spacing.xxs))
        IndicatorMenuLabel(label)
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = "+",
            color = tokens.colors.accent,
            fontSize = 12.sp,
            fontFamily = TerminalFontFamily,
        )
    }
}

@Composable
private fun IndicatorPresetListMenu(
    presets: List<IndicatorPreset>,
    onApply: (IndicatorPreset) -> Unit,
    onRename: (IndicatorPreset) -> Unit,
    onDelete: (IndicatorPreset) -> Unit,
    onSaveCurrent: () -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    if (presets.isEmpty()) {
        IndicatorMenuHint("No presets")
    }
    presets.forEach { preset ->
        val count = preset.overlays.size + preset.panes.sumOf { it.indicators.size }
        TerminalMenuItem(onClick = { onApply(preset) }) {
            Text(
                text = preset.name.uppercase(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                lineHeight = 16.sp,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                color = tokens.colors.label,
                fontSize = 8.sp,
                fontFamily = TerminalFontFamily,
                modifier = Modifier.padding(horizontal = 5.dp),
            )
            Text(
                text = "✎",
                color = tokens.colors.label,
                fontSize = 11.sp,
                fontFamily = TerminalFontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onRename(preset) }
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
            Text(
                text = "×",
                color = tokens.colors.bearish,
                fontSize = 12.sp,
                fontFamily = TerminalFontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onDelete(preset) }
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
    TerminalMenuItem(onClick = onSaveCurrent) {
        Text(
            text = "SAVE CURRENT AS…",
            color = tokens.colors.accent,
            fontSize = 9.sp,
            fontFamily = TerminalFontFamily,
        )
    }
}

@Composable
private fun IndicatorPresetNameRow(
    initial: String,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    var text by remember(initial) { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    val canSave = text.isNotBlank()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(
                color = tokens.colors.accent,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontFamily = TerminalFontFamily,
            ),
            cursorBrush = SolidColor(tokens.colors.accent),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter -> {
                            if (canSave) onCommit(text.trim())
                            true
                        }

                        Key.Escape -> {
                            onCancel()
                            true
                        }

                        else -> false
                    }
                }
                .clip(RoundedCornerShape(3.dp))
                .background(tokens.colors.selection)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Text(
            text = "SAVE",
            color = if (canSave) tokens.colors.accent else tokens.colors.label,
            fontSize = 9.sp,
            fontFamily = TerminalFontFamily,
            modifier = Modifier
                .clickable(enabled = canSave) { onCommit(text.trim()) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Text(
            text = "×",
            color = tokens.colors.bearish,
            fontSize = 12.sp,
            fontFamily = TerminalFontFamily,
            modifier = Modifier
                .clickable(onClick = onCancel)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun IndicatorMenuLabel(text: String) {
    Text(
        text = text,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontFamily = TerminalFontFamily,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun IndicatorInstanceRow(
    instance: IndicatorInstance,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onParamChange: (String, Double) -> Unit,
    onColorChange: (String, Long?) -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    val definition = indicatorDefinition(instance.type)
    val colorTargets = definition.lines.filter { it.colorLabel != null }
    val hasSettings = definition.parameters.isNotEmpty() || colorTargets.isNotEmpty()
    TerminalMenuItem(onClick = { if (hasSettings) onToggle() }) {
        Text(
            text = indicatorInstanceTitle(instance),
            color = if (expanded) tokens.colors.accent else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            lineHeight = 16.sp,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hasSettings) {
            Text(
                text = if (expanded) "▾" else "SET",
                color = tokens.colors.label,
                fontSize = 8.sp,
                fontFamily = TerminalFontFamily,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        Text(
            text = "×",
            color = tokens.colors.bearish,
            fontSize = 12.sp,
            fontFamily = TerminalFontFamily,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .clickable(onClick = onRemove)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
    if (expanded) {
        definition.parameters.forEach { spec ->
            val value = instance.params[spec.key] ?: spec.defaultValue
            IndicatorParamRow(
                spec = spec,
                value = value,
                onValueChange = { onParamChange(spec.key, it) },
            )
        }
        if (colorTargets.isNotEmpty()) {
            IndicatorMenuSection("COLORS")
            val chartColors = remember(tokens.colors) { indicatorChartColors(tokens.colors) }
            colorTargets.forEach { line ->
                val override = instance.lineColors[line.id]
                IndicatorColorRow(
                    label = line.colorLabel ?: line.label,
                    color = override?.let { indicatorColorFromArgb(it) }
                        ?: chartColors.color(line.colorRole),
                    custom = override != null,
                    onColorSelected = { colorArgb -> onColorChange(line.id, colorArgb) },
                )
            }
        }
    }
}

@Composable
private fun IndicatorParamRow(
    spec: IndicatorParamSpec,
    value: Double,
    onValueChange: (Double) -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    val focusManager = LocalFocusManager.current
    var text by remember(spec.key, value) {
        mutableStateOf(formatIndicatorParam(value, spec.integer))
    }
    var hadFocus by remember { mutableStateOf(false) }

    fun commit() {
        val parsed = parseIndicatorParam(text, spec)
        if (parsed != null) onValueChange(parsed)
        text = formatIndicatorParam(parsed ?: value, spec.integer)
    }

    TerminalMenuItem(onClick = {}) {
        Text(
            text = spec.label,
            color = tokens.colors.label,
            fontSize = 9.sp,
            lineHeight = 16.sp,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IndicatorStepButton("−") {
            onValueChange(stepIndicatorParam(value, spec, -1))
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(
                color = tokens.colors.accent,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontFamily = TerminalFontFamily,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(tokens.colors.accent),
            modifier = Modifier
                .width(44.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tokens.colors.selection)
                .onFocusChanged { state ->
                    if (hadFocus && !state.isFocused) commit()
                    hadFocus = state.isFocused
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter -> {
                            commit()
                            focusManager.clearFocus()
                            true
                        }

                        Key.Escape -> {
                            text = formatIndicatorParam(value, spec.integer)
                            focusManager.clearFocus()
                            true
                        }

                        else -> false
                    }
                }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        IndicatorStepButton("+") {
            onValueChange(stepIndicatorParam(value, spec, 1))
        }
    }
}

@Composable
private fun IndicatorStepButton(label: String, onClick: () -> Unit) {
    val tokens = LocalTerminalDesignTokens.current
    Text(
        text = label,
        color = tokens.colors.accent,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontFamily = TerminalFontFamily,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(tokens.colors.selection)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}
