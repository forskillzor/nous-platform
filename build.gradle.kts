/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

allprojects {
    version = "0.1.0"
}

subprojects {
    pluginManager.withPlugin("java-base") {
        val jdk25Launcher = extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
        tasks.withType<Test>().configureEach {
            javaLauncher.set(jdk25Launcher)
        }
    }
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false

}
