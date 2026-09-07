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
        namespace = "com.awakekt.awake.scene.world"
    }

    sourceSets {
        commonMain.dependencies {
            // Partitioning reads positions and the floating origin rewrites them, so this needs the
            // transform vocabulary. The arrow runs one way: scene-core knows nothing about cells.
            api(project(":awake:scene:scene-core"))
            // AsyncWorldCellStream and CompositeCellStreamListener load cells off the
            // frame thread; the dependency travelled with them out of scene-core.
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
