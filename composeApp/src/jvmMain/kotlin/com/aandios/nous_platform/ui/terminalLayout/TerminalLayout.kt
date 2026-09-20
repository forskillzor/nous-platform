package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.core.ui.workspace.ProjectTree
import com.aandios.nous.core.workspace.WorkspaceConfig
import com.aandios.nous.core.workspace.WorkspaceRepository
import com.aandios.nous.core.workspace.viewmodel.TabManager
import kotlinx.coroutines.launch
import nous_platform.composeapp.generated.resources.*

// Сначала создадим enum для типов инструментов
enum class ToolPanelType {
    WORKSPACES,
    SYMBOLS,
    INDICATORS,
    TIMEFRAMES,
    DRAWINGS,
    STRATEGIES
}

data class ToolPanelState(
    val isExpanded: Boolean = false,
    val type: ToolPanelType? = null,
    val width: Dp = 200.dp
)

enum class BottomToolType {
    PORTFOLIO,
    CONSOLE,
    EDITOR
}

enum class PortfolioTab {
    POSITIONS,
    ORDERS,
    BALANCE,
    STATS
}

// Мок данные для портфеля
data class MockPosition(
    val symbol: String,
    val side: TradeSide,
    val quantity: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val pnl: Double,
    val pnlPercent: Double
)

data class MockOrder(
    val id: String,
    val symbol: String,
    val side: TradeSide,
    val type: String, // "LIMIT" or "MARKET"
    val price: Double,
    val quantity: Double,
    val filled: Double,
    val timestamp: Long,
    val status: String // "OPEN", "FILLED", "CANCELLED"
)

data class MockBalance(
    val asset: String,
    val free: Double,
    val locked: Double,
    val total: Double,
    val usdValue: Double
)

@Composable
fun TerminalLayout(
    modifier: Modifier = Modifier,
    terminalState: TerminalStateViewModel,
    onSymbolSelected: (String) -> Unit,
    onTimeframeSelected: (String) -> Unit,
    tabManager: TabManager? = null,
    workspaceRepo: WorkspaceRepository? = null,
    onOpenWorkspace: ((WorkspaceConfig) -> Unit)? = null,
    mainContent: @Composable ColumnScope.() -> Unit,
) {
    var topPanelState by remember { mutableStateOf(ToolPanelState()) }
    var bottomPanelState by remember { mutableStateOf<BottomToolType?>(null) }
    val selectedSymbol by remember { mutableStateOf<String>(terminalState.selectedSymbol.value) }
    val selectedTimeframe by remember { mutableStateOf<String>(terminalState.selectedTimeFrame.value) }

    // Workspace state
    val scope = rememberCoroutineScope()
    val useWorkspaces = tabManager != null && workspaceRepo != null
    var allWorkspaceConfigs by remember { mutableStateOf<List<WorkspaceConfig>>(emptyList()) }
    if (useWorkspaces) {
        LaunchedEffect(Unit) { allWorkspaceConfigs = workspaceRepo!!.getAll() }
    }

    // Recollect after changes
    fun refreshWorkspaces() {
        scope.launch {
            allWorkspaceConfigs = workspaceRepo?.getAll() ?: emptyList()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        // Используем Row для размещения основной панели и выдвижных панелей рядом
        Row(
//            modifier = modifier.fillMaxHeight()
        ) {
            // Основная панель с иконками (всегда видима)
            Column(
                modifier = Modifier
//                    .fillMaxHeight()
                    .width(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                // Верхняя группа иконок
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Workspaces icon (only when workspace system enabled)
                    if (useWorkspaces) {
                        ToolBarIcon(
                            icon = Res.drawable.code,
                            description = "Workspaces",
                            isSelected = topPanelState.type == ToolPanelType.WORKSPACES && topPanelState.isExpanded,
                            onClick = {
                                topPanelState = if (topPanelState.type == ToolPanelType.WORKSPACES) {
                                    topPanelState.copy(isExpanded = !topPanelState.isExpanded)
                                } else {
                                    topPanelState.copy(isExpanded = true, type = ToolPanelType.WORKSPACES)
                                }
                            }
                        )
                    }

                    ToolBarIcon(
                        icon = Res.drawable.candlestick,
                        description = "Symbols",
                        isSelected = topPanelState.type == ToolPanelType.SYMBOLS && topPanelState.isExpanded,
                        onClick = {
                            topPanelState = if (topPanelState.type == ToolPanelType.SYMBOLS) {
                                topPanelState.copy(isExpanded = !topPanelState.isExpanded)
                            } else {
                                topPanelState.copy(isExpanded = true, type = ToolPanelType.SYMBOLS)
                            }
                        }
                    )

                    ToolBarIcon(
                        icon = Res.drawable.indicators,
                        description = "Indicators",
                        isSelected = topPanelState.type == ToolPanelType.INDICATORS && topPanelState.isExpanded,
                        onClick = {
                            topPanelState = if (topPanelState.type == ToolPanelType.INDICATORS) {
                                topPanelState.copy(isExpanded = !topPanelState.isExpanded)
                            } else {
                                topPanelState.copy(isExpanded = true, type = ToolPanelType.INDICATORS)
                            }
                        }
                    )

                    ToolBarIcon(
                        icon = Res.drawable.clock,
                        description = "Timeframes",
                        isSelected = topPanelState.type == ToolPanelType.TIMEFRAMES && topPanelState.isExpanded,
                        onClick = {
                            topPanelState = if (topPanelState.type == ToolPanelType.TIMEFRAMES) {
                                topPanelState.copy(isExpanded = !topPanelState.isExpanded)
                            } else {
                                topPanelState.copy(isExpanded = true, type = ToolPanelType.TIMEFRAMES)
                            }
                        }
                    )

                    ToolBarIcon(
                        icon = Res.drawable.pencil,
                        description = "Drawings",
                        isSelected = topPanelState.type == ToolPanelType.DRAWINGS && topPanelState.isExpanded,
                        onClick = {
                            topPanelState = if (topPanelState.type == ToolPanelType.DRAWINGS) {
                                topPanelState.copy(isExpanded = !topPanelState.isExpanded)
                            } else {
                                topPanelState.copy(isExpanded = true, type = ToolPanelType.DRAWINGS)
                            }
                        }
                    )

                    ToolBarIcon(
                        icon = Res.drawable.robot,
                        description = "Strategies",
                        isSelected = topPanelState.type == ToolPanelType.STRATEGIES && topPanelState.isExpanded,
                        onClick = {
                            topPanelState = if (topPanelState.type == ToolPanelType.STRATEGIES) {
                                topPanelState.copy(isExpanded = !topPanelState.isExpanded)
                            } else {
                                topPanelState.copy(isExpanded = true, type = ToolPanelType.STRATEGIES)
                            }
                        }
                    )
                }

                // Разделитель
                Divider(
                    color = MaterialTheme.colorScheme.outline,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Нижняя группа иконок
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ToolBarIcon(
                        icon = Res.drawable.wallet,
                        description = "Portfolio",
                        isSelected = bottomPanelState == BottomToolType.PORTFOLIO,
                        onClick = {
                            bottomPanelState = if (bottomPanelState == BottomToolType.PORTFOLIO) null
                            else BottomToolType.PORTFOLIO
                        }
                    )

                    ToolBarIcon(
                        icon = Res.drawable.terminal,
                        description = "Console",
                        isSelected = bottomPanelState == BottomToolType.CONSOLE,
                        onClick = {
                            bottomPanelState = if (bottomPanelState == BottomToolType.CONSOLE) null
                            else BottomToolType.CONSOLE
                        }
                    )

                    ToolBarIcon(
                        icon = Res.drawable.code,
                        description = "Editor",
                        isSelected = bottomPanelState == BottomToolType.EDITOR,
                        onClick = {
                            bottomPanelState = if (bottomPanelState == BottomToolType.EDITOR) null
                            else BottomToolType.EDITOR
                        }
                    )
                }
            }

            // Верхняя выдвижная панель (слева, под иконками)
            if (topPanelState.isExpanded) {
                // Draggable handle state
                val sidebarInteractionSource = remember { MutableInteractionSource() }
                val sidebarHovered by sidebarInteractionSource.collectIsHoveredAsState()
                Row {
                    if (topPanelState.type == ToolPanelType.WORKSPACES && useWorkspaces) {
                        ProjectTree(
                            workspaces = allWorkspaceConfigs,
                            activeId = tabManager!!.activeWorkspace?.config?.id,
                            onWorkspaceClick = { config -> onOpenWorkspace?.invoke(config) },
                            onNewWorkspace = {
                                scope.launch {
                                    val config = com.aandios.nous.core.workspace.Templates.scalping()
                                    workspaceRepo!!.create(config)
                                    onOpenWorkspace?.invoke(config)
                                    refreshWorkspaces()
                                }
                            },
                            onRename = { ws, newName ->
                                scope.launch { workspaceRepo?.update(ws.copy(name = newName)); refreshWorkspaces() }
                            },
                            onDelete = { ws ->
                                scope.launch { tabManager?.closeWorkspace(ws.id); workspaceRepo?.delete(ws.id); refreshWorkspaces() }
                            },
                            onExport = { ws ->
                                scope.launch {
                                    val json = workspaceRepo?.exportJson(ws) ?: ""
                                    println("=== Workspace Export: ${ws.name} ===\n$json\n=== END ===")
                                }
                            },
                            onDuplicate = { ws ->
                                scope.launch {
                                    val copy = ws.copy(id = com.aandios.nous.core.workspace.generateId(), name = "${ws.name} (copy)")
                                    workspaceRepo?.create(copy); refreshWorkspaces()
                                }
                            },
                            modifier = Modifier.width(topPanelState.width).fillMaxHeight()
                        )
                    } else {
                        ToolDetailsPanel(
                            type = topPanelState.type ?: ToolPanelType.SYMBOLS,
                            width = topPanelState.width,
                            selectedSymbol = selectedSymbol,
                            onSymbolSelected = onSymbolSelected,
                            selectedTimeframe = selectedTimeframe,
                            onTimeframeSelected = onTimeframeSelected,
                            onClose = { topPanelState = topPanelState.copy(isExpanded = false) },
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                    // Draggable right border
                    Box(
                        modifier = Modifier
                            .width(4.dp).fillMaxHeight()
                            .background(if (sidebarHovered) Color(0xFF00C853).copy(alpha = 0.4f) else Color(0xFF333333))
                            .hoverable(sidebarInteractionSource)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    topPanelState = topPanelState.copy(
                                        width = (topPanelState.width + dragAmount.dp).coerceIn(140.dp, 500.dp)
                                    )
                                }
                            }
                    )
                }
            }
            Column() {
                mainContent()
                if (bottomPanelState != null) {

                    BottomToolPanel(
                        type = bottomPanelState!!,
                        width = Dp.Unspecified,
                        onClose = { bottomPanelState = null },
                        modifier = Modifier.fillMaxWidth()
                            .height(300.dp)
                            .weight(0.5f)
                    )
                }
            }
        }
    }
}

