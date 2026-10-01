/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpLibraryConvention : Plugin<Project> {
    @OptIn(ExperimentalWasmDsl::class)
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                jvm()
                js(IR) { browser() }
                wasmJs { browser() }
            }
        }
    }
}