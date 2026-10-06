/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.core.ui.component.PanelSymbolWatermark
import com.aandios.nous.feature.trades.ui.header.TradesHeaderBar

/**
 * Панель для отображения потока сделок (Trades).
 * Использует MaterialTheme цвета (как feature-dom) + кастомный header с dropdowns.
 */
@Composable
fun TradesWidget(
    viewModel: TradesViewModel,
    currentSymbol: String,
    onSymbolChanged: (String) -> Unit,
    providers: List<com.aandios.nous.api.market.Provider> = emptyList(),
    onProviderChanged: (String) -> Unit = {},
    /** Максимальная ширина панели (шестерёнка настроек). */
    maxPanelWidth: androidx.compose.ui.unit.Dp = 240.dp,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val loadedSymbols by viewModel.loadedSymbols.collectAsState()
    val currentSymbolInfo by viewModel.currentSymbolInfo.collectAsState()
    val selectedSizeFilter by viewModel.selectedSizeFilter.collectAsState()
    val customPresets by viewModel.customPresets.collectAsState()
    val currentProviderId by viewModel.currentProviderId.collectAsState()
    val contractType by viewModel.contractType.collectAsState()
    // Подписка на буфер отфильтрованных сделок — триггерит рекомпозицию
    // при reseed фильтра, даже если фид не менялся.
    val filteredBuffer by viewModel.filteredBuffer.collectAsState()

    val lazyListState = rememberLazyListState()
    var autoScrollEnabled by remember { mutableStateOf(true) }
    // Мин. ширина для слайдера настроек: измеряем текст строки сделки
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var measuredMinWidth by remember { mutableStateOf(120.dp) }
    val panelWidthDp by viewModel.panelWidthDp.collectAsState()

    // Автоскролл живёт, пока пользователь у вершины списка. Выключаем только
    // на РУЧНОЙ прокрутке вниз (программная вставка новых сделок сверху
    // сдвигает index без scroll — это не ручная прокрутка).
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.isScrollInProgress }
            .collect { (index, inProgress) ->
                if (index == 0 && !inProgress) autoScrollEnabled = true
                else if (inProgress && index > 0) autoScrollEnabled = false
            }
    }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        // Header bar с dropdowns (как в feature-dom)
        TradesHeaderBar(
            currentSymbol = currentSymbol,
            availableSymbols = loadedSymbols,
            currentSymbolInfo = currentSymbolInfo,
            selectedSizeFilter = selectedSizeFilter,
            customPresets = customPresets,
            providers = providers,
            currentProviderId = currentProviderId,
            contractType = contractType,
            panelWidthDp = panelWidthDp ?: maxPanelWidth.value,
            minPanelWidthDp = measuredMinWidth.value,
            maxPanelWidthDp = maxPanelWidth.value,
            onPanelWidthDpChanged = { viewModel.setPanelWidth(it) },
            onPanelWidthDpChangeFinished = { viewModel.persistPanelWidth() },
            onProviderChanged = onProviderChanged,
            onContractTypeChanged = { viewModel.setContractType(it) },
            onSymbolChanged = onSymbolChanged,
            onFilterChanged = { viewModel.updateSizeFilter(it) },
            onPresetAdd = { viewModel.addPreset(it) },
            onPresetEdit = { idx, v -> viewModel.editPreset(idx, v) },
            onPresetDelete = { viewModel.deletePreset(it) },
        )

        // Заголовки колонок
        ColumnHeaderRow()

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )

        // Контент (водяной знак — нижним слоем)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PanelSymbolWatermark(
                symbolInfo = currentSymbolInfo,
                currentSymbol = currentSymbol,
                exchange = providers.firstOrNull { it.providerId == currentProviderId }
                    ?.config?.displayName,
                modifier = Modifier.align(Alignment.Center),
            )
            when (val currentState = state) {
            is TradesState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Подключение...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
            is TradesState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentState.message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
            is TradesState.Connected -> {
                val visibleTrades = viewModel.visibleTrades(currentState.trades)

                // Минимум для слайдера ширины: время + цена + кол-во
                // + 4dp между колонками + внешние отступы 8dp с каждой стороны
                if (visibleTrades.isNotEmpty()) {
                    val maxQty = visibleTrades.maxOfOrNull { it.quantity } ?: 1.0
                    val maxPrice = visibleTrades.maxOfOrNull { it.price } ?: 0.0
                    val sampleStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    val wTime = measurer.measure(
                        androidx.compose.ui.text.AnnotatedString(
                            viewModel.formatTime(visibleTrades.first().timestamp)
                        ),
                        sampleStyle,
                    ).size.width
                    val wPrice = measurer.measure(
                        androidx.compose.ui.text.AnnotatedString(viewModel.formatPrice(maxPrice)),
                        sampleStyle,
                    ).size.width
                    val wQty = measurer.measure(
                        androidx.compose.ui.text.AnnotatedString(viewModel.formatQuantity(maxQty)),
                        sampleStyle,
                    ).size.width
                    val gapsPx = with(density) { 4.dp.toPx() * 2 + 8.dp.toPx() * 2 }
                    val minWidth = with(density) { (wTime + wPrice + wQty).toFloat().plus(gapsPx).toDp() }
                    LaunchedEffect(minWidth) { measuredMinWidth = minWidth }
                }

                if (visibleTrades.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedSizeFilter !is SizeFilter.All) "Нет сделок > фильтра" else "Ожидание данных...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    val maxQuantity = visibleTrades.maxOfOrNull { it.quantity } ?: 1.0

                    // Новая сделка сверху: если автоскролл включён — сразу к ней
                    val newestId = visibleTrades.firstOrNull()?.id
                    LaunchedEffect(newestId) {
                        if (autoScrollEnabled && newestId != null) {
                            lazyListState.scrollToItem(0)
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(visibleTrades, key = { _, trade -> trade.id }) { index, trade ->
                            TradeRow(
                                trade = trade,
                                viewModel = viewModel,
                                maxQuantity = maxQuantity,
                                index = index
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * Строка заголовков колонок: Время / Цена / Кол-во.
 */
@Composable
private fun ColumnHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = "Время",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Цена",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Кол-во",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f)
        )
    }
}

/**
 * Строка одной сделки.
 * Цвета: buy = MaterialTheme.colorScheme.primary, sell = MaterialTheme.colorScheme.secondary.
 */
@Composable
private fun TradeRow(
    trade: Trade,
    viewModel: TradesViewModel,
    maxQuantity: Double,
    index: Int
) {
    val bgColor = if (index % 2 == 0) Color.Transparent
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    val priceColor = if (trade.isBuyerMaker) MaterialTheme.colorScheme.secondary  // продажа (красный)
    else MaterialTheme.colorScheme.primary  // покупка (зелёный)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Время
        Text(
            text = viewModel.formatTime(trade.timestamp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )

        // Цена
        Text(
            text = viewModel.formatPrice(trade.price),
            color = priceColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )

        // Количество с горизонтальной гистограммой объема
        Box(
            modifier = Modifier
                .weight(1.4f)
                .height(20.dp)
        ) {
            // Горизонтальный bar объема (пропорционально maxQuantity)
            val volumeWidth = (trade.quantity / maxQuantity).coerceIn(0.0, 1.0)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(volumeWidth.toFloat())
                    .align(Alignment.CenterEnd)
                    .background(priceColor.copy(alpha = 0.25f))
            )
            // Текст количества поверх бара
            Text(
                text = viewModel.formatQuantity(trade.quantity),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
