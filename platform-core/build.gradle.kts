/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

plugins {
    id("conventions.kmp-library")
    id("conventions.kmp-application")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-dependencies"))
            implementation(project(":public-api:api-market"))
            implementation(project(":providers:binance-provider"))

            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            api(libs.ktor.serialization.kotlinx.json)
            api(libs.ktor.client.core)
            api(libs.ktor.client.websockets)
            api(libs.ktor.client.content.negotiation)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }

        jvmMain {
            resources.srcDir("src/jvmMain/resources")
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.components.uiToolingPreview)
            api(libs.koin.core)
            api(libs.koin.compose)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit.jupiter)
            implementation(libs.kotlinx.coroutines.test)
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.uiTestJUnit4)
        }
    }
}