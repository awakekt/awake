/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("awake.kmp-library-convention")
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.compose.foundation"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:compose:ui"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Compose Foundation")
        description.set("Row, Column, Box, scrolling and the layout modifiers built on the Compose UI node tree")
    }
}
