/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

plugins {
    id("conventions.kmp-feature")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":platform-core"))
            implementation(project(":public-api:api-market"))
            implementation(project(":providers:binance-provider"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.core)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(project(":features:localstorage"))
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.aandios.nous.feature.trading.ui.TradingWindowKt"
    }
}
