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
            implementation(project(":public-api:api-market"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            implementation(libs.ta4j.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit.jupiter)
        }
    }
}