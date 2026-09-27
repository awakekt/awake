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
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.awakekt.awake.kit.terrainlayers"
    }

    sourceSets {
        commonMain.dependencies {
            // TerrainSurface and TerrainSurfaceProvider, and through them the shared clipmap stage.
            api(project(":awake:scene:scene3d"))
            api(project(":awake:core:io"))
            // createBitmap: layer albedo and height images are ordinary PNGs.
            implementation(project(":awake:core:image"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Kit Terrain Layers")
        description.set("Layered terrain surface: layer palette, top-4 control map, and its shader")
    }
}
