/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.hot.reload)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain {
            resources.srcDirs("src/jvmMain/resources")  // исправлено!
        }

        commonMain.dependencies {
            // Modules
            implementation(project(":features:chart"))
            implementation(project(":features:dom"))
            implementation(project(":features:trades"))
            implementation(project(":features:localstorage"))
            implementation(project(":features:settings"))
            implementation(project(":platform-core"))
            implementation(project(":public-api:api-market"))
            implementation(project(":providers:binance-provider"))

            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // kotlinx datetime
            implementation(libs.kotlinx.datetime)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            // Compose
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.ui.geometry.desktop)
            implementation(libs.kotlinx.coroutines.swing)
        }

        jvmTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.aandios.nous-platform.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.aandios.nous-platform"
            packageVersion = version.toString()

            buildTypes.release.proguard {
                obfuscate.set(false)
                optimize.set(false)

                configurationFiles.from(
                    providers.provider {
                        val proguardFile = layout.buildDirectory.dir("tmp").get().asFile.resolve("proguard-rules.pro")  // исправлено!
                        proguardFile.parentFile.mkdirs()

                        proguardFile.writeText("""
                            # Отключаем всё, что может сломать сборку
                            -dontobfuscate
                            -dontoptimize
                            -dontshrink
                            -dontpreverify
                            -ignorewarnings
                            
                            # Игнорируем ВСЕ warnings
                            -dontwarn **
                            
                            # Сохраняем наш код
                            -keep class com.aandios.tradingterminal.** { *; }
                            -keep class com.aandios.tradingterminal.**$* { *; }
                            
                            # Сохраняем Kotlin
                            -keep class kotlin.** { *; }
                            -keep class kotlinx.** { *; }
                            
                            # Сохраняем Compose
                            -keep class androidx.compose.** { *; }
                            -keep class androidx.ui.** { *; }
                            
                            # Сохраняем Ktor
                            -keep class io.ktor.** { *; }
                            
                            # Сохраняем Koin
                            -keep class org.koin.** { *; }
                            
                            # Сохраняем сериализацию
                            -keep class kotlinx.serialization.** { *; }
                            
                            # Сохраняем корутины
                            -keep class kotlinx.coroutines.** { *; }
                            
                            # Игнорируем конкретные ошибки
                            -dontwarn androidx.compose.ui.util.ListUtilsKt
                            -dontwarn org.slf4j.**
                            -dontwarn org.slf4j.impl.**
                            -dontwarn kotlinx.coroutines.internal.LockFreeLinkedListHead
                            -dontwarn androidx.compose.ui.geometry.MutableRect
                            
                            # Разрешаем дубликаты ресурсов
                            -ignorewarnings
                        """.trimIndent())

                        proguardFile
                    }
                )
            }

            windows {
                menu = true
            }
            macOS {
                dmgPackageVersion = "1.0.0"
            }
            linux {
                shortcut = true
            }
        }
    }
}