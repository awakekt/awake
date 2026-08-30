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
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.engine.compose"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:engine:platform"))
            api(project(":awake:compose:ui"))
            api(project(":awake:core:text"))
        }
        commonTest.dependencies {
            implementation(project(":awake:engine:render:testing"))
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
