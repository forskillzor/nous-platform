/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import com.aandios.nous.feature.dom.di.initKoinForPreview
import com.aandios.nous.feature.dom.ui.content.DomContent
import com.aandios.nous.feature.dom.ui.footer.OrderPlacementPanel
import com.aandios.nous.feature.dom.ui.header.DomHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.context.stopKoin

@Composable
fun DomWindow() {
    val domViewModel: DomViewModel = koinInject()
    DomWindow(domViewModel = domViewModel)
}

/** Рекомендуемая ширина DOM-панели в workspace. */
val DomRecommendedWidth: Dp = 240.dp

@Composable
fun DomWindow(
    domViewModel: DomViewModel,
    modifier: Modifier = Modifier,
) {
    val registry: ProviderRegistry = koinInject()
    val domOptions by domViewModel.domOptions.collectAsState()
    val loadedSymbols by domViewModel.loadedSymbols.collectAsState()
    val orderQuantity by domViewModel.orderQuantity.collectAsState()
    val isTradingEnabled by domViewModel.isTradingEnabled.collectAsState()
    val symbolTickSize by domViewModel.symbolTickSize.collectAsState()
    val symbolStepSize by domViewModel.symbolStepSize.collectAsState()
    val selectedPrice by domViewModel.selectedPrice.collectAsState()
    val reduceOnly by domViewModel.reduceOnly.collectAsState()
    val limitOrderType by domViewModel.limitOrderType.collectAsState()
    val leverage by domViewModel.leverage.collectAsState()
    val paperEnabled by domViewModel.paperEnabled.collectAsState()
    val confirmOrders by domViewModel.confirmOrders.collectAsState()
    val pendingIntentText by domViewModel.pendingIntentText.collectAsState()
    val notifications by domViewModel.notifications.collectAsState()
    val orders by domViewModel.orders.collectAsState()
    val positions by domViewModel.positions.collectAsState()
    val markPrice by domViewModel.markPrice.collectAsState()
    val baseText = domOptions.symbol.symbolInfo?.baseAsset
        ?: domOptions.symbol.displayName.substringBefore("/").takeIf { it.isNotBlank() }

    // Одно состояние лучших цен
    val bestPrices by domViewModel.bestPrices.collectAsState()

    // Таблица уровней для лесенки (SnapshotStateMap — чтение по ключу реактивно)
    val levelsMap = domViewModel.levelsMap
    val ladderStepTicks = domViewModel.ladderStepTicks

    val formatter = remember(symbolTickSize, symbolStepSize) {
        SymbolFormatter(
            tickSize = symbolTickSize ?: 0.01,
            minQty = symbolStepSize ?: 0.001
        )
    }

    val bestBidPrice = bestPrices.bestBid ?: 0.0
    val bestAskPrice = bestPrices.bestAsk ?: 0.0

    Column(modifier = modifier.fillMaxSize()) {
        DomHeader(
            domOptions = domOptions,
            symbolTickSize = symbolTickSize,
            loadedSymbols = loadedSymbols,
            providers = registry.providers,
            onDomOptionsChanged = { newOptions -> domViewModel.updateDomOptions(newOptions) }
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)
                .fillMaxWidth()
        ) {
            DomContent(
                levelsMap = levelsMap,
                ladderStepTicks = ladderStepTicks,
                selectedPrice = selectedPrice,
                bestBidDisplayTicks = bestPrices.bestBidDisplayTicks,
                bestAskDisplayTicks = bestPrices.bestAskDisplayTicks,
                lastPriceDisplayTicks = bestPrices.lastPriceDisplayTicks,
                tickSize = symbolTickSize ?: 0.01,
                stepSize = symbolStepSize ?: 0.001,
                formatter = formatter,
                onPriceSelected = { price -> domViewModel.selectPrice(price) },
                orders = orders,
                positions = positions,
                markPrice = markPrice,
                baseText = baseText,
                onCancelOrder = { orderId -> domViewModel.cancelDomOrder(orderId) },
                modifier = Modifier.fillMaxSize()
            )
            // Snackbar-уведомления DOM — под заголовком (как в chart trading)
            if (notifications.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .widthIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    notifications.forEach { n ->
                        DomNotificationItem(n) { domViewModel.dismissNotification(it) }
                    }
                }
            }
        }
        OrderPlacementPanel(
            symbol = domOptions.symbol.symbol,
            selectedPrice = selectedPrice,
            orderQuantity = orderQuantity,
            bestBidPrice = bestBidPrice,
            bestAskPrice = bestAskPrice,
            onQuantityChanged = { qty -> domViewModel.updateOrderQuantity(qty) },
            onOrderIntent = { intent -> domViewModel.handleOrderIntent(intent) },
            onCloseAll = { domViewModel.closeAllPositions() },
            onCancelAll = { domViewModel.cancelAllOrders() },
            isTradingEnabled = isTradingEnabled,
            reduceOnly = reduceOnly,
            limitOrderType = limitOrderType,
            leverage = leverage,
            paperEnabled = paperEnabled,
            confirmOrders = confirmOrders,
            pendingText = pendingIntentText,
            onReduceOnlyChanged = { domViewModel.setReduceOnly(it) },
            onLimitOrderTypeChanged = { domViewModel.setLimitOrderType(it) },
            onLeverageChanged = { domViewModel.setLeverage(it) },
            onPaperChanged = { domViewModel.setPaperEnabled(it) },
            onConfirmChanged = { domViewModel.setConfirmOrders(it) },
            onConfirmPending = { domViewModel.confirmPendingIntent() },
            onCancelPending = { domViewModel.cancelPendingIntent() },
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        )
    }
}

/** Snackbar DOM: слайд сверху, авто-скрытие ~4.5с, крестик. */
@Composable
private fun DomNotificationItem(
    notification: DomNotification,
    onDismiss: (Long) -> Unit,
) {
    var visible by remember(notification.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(notification.id) {
        visible = true
        delay(4500)
        visible = false
        delay(200)
        onDismiss(notification.id)
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color(0xFF14181F).copy(alpha = 0.95f), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF3A4550), RoundedCornerShape(4.dp))
                .padding(start = 8.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
        ) {
            Text(
                text = notification.text,
                color = Color(0xFFD5DBE1),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "✕",
                color = Color(0xFF8A97A5),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier
                    .clickable {
                        visible = false
                        scope.launch {
                            delay(200)
                            onDismiss(notification.id)
                        }
                    }
                    .padding(horizontal = 6.dp),
            )
        }
    }
}

fun main() = application {
    stopKoin()
    initKoinForPreview()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Nous Platform • DOM Preview",
        state = rememberWindowState(width = 300.dp, height = 800.dp)
    ) {
        KoinContext {
            TradingTerminalTheme {
                DomWindow()
            }
        }
    }
}
