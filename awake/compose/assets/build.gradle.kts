/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.awakekt.awake.plugin.kmp-library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":awake:compose:runtime"))
            // ImageBitmap and decodeImageBitmap.
            api(project(":awake:compose:ui"))
            api(project(":awake:core:io"))
            api(libs.kotlinx.coroutines.core)
            implementation(project(":awake:core:logging"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.named<Test>("desktopTest") {
    // AssetImageMapAllocationTest counts every byte; with escape analysis on, C2 hides short-lived
    // objects that Android's runtime still allocates, as for the compose modules (#1521).
    jvmArgs("-XX:-DoEscapeAnalysis")
}

mavenPublishing {
    pom {
        name.set("Awake Compose Assets")
        description.set("Images by asset path for Awake Compose UI: the project's asset source and a per-scene image cache, through CompositionLocals")
    }
}
