/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("awake.kmp-library-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.compose.testing"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:compose:foundation"))
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:engine:render:testing"))
            implementation(project(":awake:core:text"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
