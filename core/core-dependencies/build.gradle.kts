/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

plugins {
    id("conventions.kmp-library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            api(libs.ktor.serialization.kotlinx.json)
            api(libs.ktor.client.core)
            api(libs.ktor.client.websockets)
            api(libs.ktor.client.content.negotiation)
            api(compose.runtime)
        }

        jvmMain.dependencies {
            api(compose.desktop.currentOs)
            api(libs.koin.core)
            api(libs.koin.compose)
            api(libs.ktor.client.cio)
        }

        val wasmJsMain by getting
        wasmJsMain.dependencies {
        }
    }
}