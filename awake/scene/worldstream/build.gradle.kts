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
        namespace = "com.awakekt.awake.scene.worldstream"
    }

    sourceSets {
        commonMain.dependencies {
            // Each streamer is an AsyncWorldCellStreamListener that fills a streamed cell with one
            // kind of content: meshes, physics bodies, heightfield tiles, terrain texture tiles.
            api(project(":awake:scene:world"))
            api(project(":awake:scene:physics"))
            api(project(":awake:scene:scene3d"))
            api(project(":awake:scene:runtime"))
            api(project(":awake:physics:api"))
            api(project(":awake:asset:terrain"))
            api(project(":awake:engine:render:contract"))
            api(project(":awake:core:geometry"))
            api(project(":awake:core:math"))
            api(project(":awake:ecs"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":awake:engine:render:testing"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Worldstream")
        description.set("Cell streamers that load meshes, physics, heightfield tiles and terrain texture tiles as a world streams")
    }
}
