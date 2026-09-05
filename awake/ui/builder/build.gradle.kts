/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("awake.kmp-library-convention")
    id("awake.publish-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.ui.builder"
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
            implementation(project(":awake:editor:core"))
            implementation(project(":awake:editor"))
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
