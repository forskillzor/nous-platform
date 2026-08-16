/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Эти зависимости нужны для convention плагинов
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    implementation("org.jetbrains.compose:compose-gradle-plugin:1.7.0")
}

gradlePlugin {
    plugins {
        create("kmp-feature") {
            id = "conventions.kmp-feature"
            implementationClass = "conventions.KmpFeatureConvention"
        }
        create("kmp-library") {
            id = "conventions.kmp-library"
            implementationClass = "conventions.KmpLibraryConvention"
        }
        create("kmp-application") {
            id = "conventions.kmp-application"
            implementationClass = "conventions.KmpApplicationConvention"
        }
    }
}