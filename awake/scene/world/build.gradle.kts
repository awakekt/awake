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
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.scene.world"
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
