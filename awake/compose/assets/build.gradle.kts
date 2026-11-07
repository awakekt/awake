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
}

kotlin {
    android {
        namespace = "com.awakekt.awake.compose.assets"
    }

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

mavenPublishing {
    pom {
        name.set("Awake Compose Assets")
        description.set("Images by asset path for Awake Compose UI: the project's asset source and a per-scene image cache, through CompositionLocals")
    }
}
