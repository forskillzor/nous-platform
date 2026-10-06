/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.window

import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

private val isWindows: Boolean
    get() = System.getProperty("os.name").lowercase().contains("win")

/** Минимальный JNA-биндинг к dwmapi.dll — только то, что нужно. */
private interface DwmApi : Library {
    fun DwmSetWindowAttribute(hWnd: Pointer, dwAttribute: Int, pvAttribute: Pointer, cbAttribute: Int): Int

    companion object {
        val INSTANCE: DwmApi by lazy { Native.load("dwmapi", DwmApi::class.java) }
    }
}

/**
 * Тёмный фон AWT-окна. Вызывать в `init`-блоке [androidx.compose.ui.awt.v2.SwingWindow]
 * — ДО того, как окно станет displayable: тогда нативный background brush
 * создаётся уже тёмным, и при живом ресайзе AWT заливает вновь открытую
 * область тёмным цветом, а не белым (работает и на Windows, и на Linux).
 */
fun applyWindowDarkBackground(window: ComposeWindow, darkRgb: Int = 0x0A0A0A) {
    val awtColor = Color(darkRgb)
    window.background = awtColor
    window.rootPane.background = awtColor
    window.contentPane.background = awtColor
}

fun applyWindowIcon(window: ComposeWindow, resourcePath: String) {
    try {
        val stream = object {}.javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            ?: return
        stream.use { input ->
            val image: BufferedImage = ImageIO.read(input) ?: return
            window.iconImage = image
        }
    } catch (e: Throwable) {
        println("⚠️ Не удалось установить иконку окна: ${e.message}")
    }
}

/**
 * Тёмный заголовок окна на Windows (DWM immersive dark mode).
 * На остальных ОС — no-op: на macOS заголовок управляется JVM-флагом
 * `apple.awt.application.appearance`, на Linux — темой оконного менеджера.
 *
 * Вызывать после показа окна (LaunchedEffect): нужен валидный windowHandle.
 * Нюанс: при смене темы Windows в рантайме DWM может сбросить атрибут —
 * применяем при старте окна.
 */
fun applyWindowsDarkTitleBar(window: ComposeWindow) {
    if (!isWindows) return

    // DWMWA_USE_IMMERSIVE_DARK_MODE: 20 на Win10 20H1+ / Win11, 19 на более старых.
    val attribute = 20
    val fallbackAttribute = 19
    try {
        val handle = Pointer(window.windowHandle)
        val value = IntByReference(1)
        val cbAttribute = 4 // sizeof(BOOL)
        val applied = DwmApi.INSTANCE.DwmSetWindowAttribute(handle, attribute, value.pointer, cbAttribute)
        if (applied != 0) {
            DwmApi.INSTANCE.DwmSetWindowAttribute(handle, fallbackAttribute, value.pointer, cbAttribute)
        }
    } catch (e: Throwable) {
        println("⚠️ Не удалось включить тёмный заголовок окна: ${e.message}")
    }
}
