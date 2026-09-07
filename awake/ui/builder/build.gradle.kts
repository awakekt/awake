/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.awakekt.awake.ui.builder"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:math"))
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:input"))
            implementation(project(":awake:tailwind"))
            implementation(project(":awake:heroicons"))
            implementation(project(":awake:compose:foundation"))
            implementation(project(":awake:ui:shadcn"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(project(":awake:compose:ui-testing"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(kotlin("test"))
        }
        desktopTest.dependencies {
            implementation(project(":awake:compose:ui-testing"))
        }
    }
}
