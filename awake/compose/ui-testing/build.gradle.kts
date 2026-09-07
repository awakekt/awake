/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.awakekt.awake.compose.testing"
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
