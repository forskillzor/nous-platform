package com.aandios.nous.core.ui.workspace

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier
) {
    val groups = workspaces.groupBy { it.group.ifEmpty { "Unsorted" } }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 140.dp)
            .background(Color(0xFF0A0A0A))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Project",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                "+", color = Color(0xFF00C853), fontSize = 14.sp, fontWeight = FontWeight.Bold,
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
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(name: String) {
    Text(
        "▸ $name", color = Color(0xFF666666), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
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
    onDuplicate: (() -> Unit)?
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editName by remember(name) { mutableStateOf(name) }

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
                            color = Color(0xFF00C853), fontSize = 12.sp, fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF00C853), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp),
                        cursorBrush = SolidColor(Color(0xFF00C853)),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                innerTextField()
                            }
                        }

                    )
                    Text(
                        "✓", color = Color(0xFF00C853), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onRename?.invoke(editName); editing = false }
                            .padding(horizontal = 4.dp)
                    )
                    Text(
                        "✗", color = Color(0xFF666666), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { editing = false; editName = name }.padding(start = 2.dp)
                    )
                }
            } else {
                Text(
                    text = name, color = if (isActive) Color(0xFF00C853) else Color(0xFFAAAAAA),
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            // Menu trigger
            Text(
                "⋮", color = Color(0xFF444444), fontSize = 14.sp, fontFamily = FontFamily.Monospace,
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
            if (editing) {
                DropdownMenuItem(text = { Text("Save Name", fontSize = 12.sp) }, onClick = {
                    menuExpanded = false; onRename?.invoke(editName); editing = false
                })
            }
            onDelete?.let { fn ->
                DropdownMenuItem(text = { Text("Delete", color = Color(0xFFF44336), fontSize = 12.sp) }, onClick = {
                    menuExpanded = false; fn()
                })
            }
        }
    }
}
