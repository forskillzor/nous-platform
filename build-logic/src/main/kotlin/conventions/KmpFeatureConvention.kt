/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpFeatureConvention : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            // ✅ Получаем доступ к каталогу версий
            val libs = extensions.getByType<VersionCatalogsExtension>()
                .named("libs")

            extensions.configure<KotlinMultiplatformExtension> {
                jvmToolchain(25)
                jvm()

                sourceSets {
                    val commonMain by getting
                    commonMain.dependencies {
                        api(project(":core:core-dependencies"))

                        // ✅ Используем каталог через findLibrary()
                        implementation(libs.findLibrary("compose.runtime").get())
                        implementation(libs.findLibrary("compose.foundation").get())
                        implementation(libs.findLibrary("compose.material3").get())
                        implementation(libs.findLibrary("compose.ui").get())
                    }
                }
            }
        }
    }
}