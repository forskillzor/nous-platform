/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace
import com.aandios.nous.core.ui.theme.TerminalFontFamily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.core.ui.component.TerminalContextPopup
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.workspace.WorkspaceConfig

@Composable
fun ProjectTree(
    workspaces: List<WorkspaceConfig>,
    activeId: String?,
    onWorkspaceClick: (WorkspaceConfig) -> Unit,
    onNewWorkspace: () -> Unit,
    onRename: ((WorkspaceConfig, String) -> Unit)? = null,
    onDelete: ((WorkspaceConfig) -> Unit)? = null,
    onExport: ((WorkspaceConfig) -> Unit)? = null,
    onDuplicate: ((WorkspaceConfig) -> Unit)? = null,
    onSaveAsTemplate: ((WorkspaceConfig) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val groups = workspaces.groupBy { it.group.ifEmpty { "Unsorted" } }
    val tokens = LocalTerminalDesignTokens.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = tokens.dimensions.workspaceProjectTreeMinWidth)
            .background(tokens.colors.panelSurface)
            .padding(tokens.spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Project",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold
            )
            Text(
                "+", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onNewWorkspace() })
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            groups.forEach { (group, wss) ->
                item { GroupHeader(group) }
                items(wss, key = { it.id }) { ws ->
                    WorkspaceTreeItem(
                        name = ws.name,
                        isActive = ws.id == activeId,
                        onClick = { onWorkspaceClick(ws) },
                        onRename = onRename?.let { fn -> { name -> fn(ws, name) } },
                        onDelete = onDelete?.let { fn -> { fn(ws) } },
                        onExport = onExport?.let { fn -> { fn(ws) } },
                        onDuplicate = onDuplicate?.let { fn -> { fn(ws) } },
                        onSaveAsTemplate = onSaveAsTemplate?.let { fn -> { fn(ws) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(name: String) {
    Text(
        "▸ $name", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = TerminalFontFamily,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun WorkspaceTreeItem(
    name: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: ((String) -> Unit)?,
    onDelete: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onDuplicate: (() -> Unit)?,
    onSaveAsTemplate: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editName by remember(name) { mutableStateOf(name) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontFamily = TerminalFontFamily
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                innerTextField()
                            }
                        }

                    )
                    Text(
                        "✓", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onRename?.invoke(editName); editing = false }
                            .padding(horizontal = 4.dp)
                    )
                    Text(
                        "✗", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { editing = false; editName = name }.padding(start = 2.dp)
                    )
                }
            } else {
                Text(
                    text = name, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp, fontFamily = TerminalFontFamily,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            // Menu trigger
            Text(
                "⋮", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp, fontFamily = TerminalFontFamily,
                modifier = Modifier.clickable { menuExpanded = true }.padding(start = 4.dp)
            )
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            onRename?.let { fn ->
                DropdownMenuItem(text = { Text("Rename", fontSize = 12.sp) }, onClick = {
                    menuExpanded = false; editing = true; editName = name
                })
            }
            onDuplicate?.let { fn ->
                DropdownMenuItem(text = { Text("Duplicate", fontSize = 12.sp) }, onClick = {
                    menuExpanded = false; fn()
                })
            }
            onExport?.let { fn ->
                DropdownMenuItem(text = { Text("Export JSON", fontSize = 12.sp) }, onClick = {
                    menuExpanded = false; fn()
                })
            }
            onSaveAsTemplate?.let { fn ->
                DropdownMenuItem(text = { Text("Save as template", fontSize = 12.sp) }, onClick = {
                    menuExpanded = false; fn()
                })
            }
            if (editing) {
                DropdownMenuItem(text = { Text("Save Name", fontSize = 12.sp) }, onClick = {
                    menuExpanded = false; onRename?.invoke(editName); editing = false
                })
            }
            onDelete?.let { fn ->
                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }, onClick = {
                    menuExpanded = false
                    confirmDelete = true
                })
            }
        }

        if (confirmDelete) {
            TerminalContextPopup(
                expanded = true,
                onDismiss = { confirmDelete = false },
                title = "Delete workspace",
                message = "Workspace \"$name\" will be permanently deleted.",
                confirmText = "Delete",
                destructive = true,
                onConfirm = {
                    confirmDelete = false
                    onDelete?.invoke()
                },
            )
        }
    }
}
