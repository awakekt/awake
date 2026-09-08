/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("com.awakekt.awake.plugin.library")
    alias(libs.plugins.kotlin.serialization)
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.scene.rendering"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math"))
            api(project(":awake:core:color"))
            implementation(project(":awake:core:animation"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:world"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            api(project(":awake:engine:render:contract"))
            api(project(":awake:asset:terrain"))
            // terrainContentFeature(TerrainComponent) adapts the pack's feature to the ECS
            // component. Acyclic: shader-pack knows nothing of the scene layer.
            api(project(":awake:asset:shader-pack"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // MeshCellStreamer uploads through a Renderer; NoopRenderer is the one that counts
            // uploads without a GPU.
            implementation(project(":awake:engine:render:testing"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Rendering")
        description.set("Camera, lights, mesh renderers, particles and the systems that turn a scene into draw calls")
    }
}
