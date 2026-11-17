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
    id("com.awakekt.awake.plugin.scene-components")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.scene.rendering"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:image"))
            implementation(project(":awake:core:math"))
            implementation(project(":awake:core:pool"))
            implementation(project(":awake:particles"))
            api(project(":awake:core:color"))
            // DebugVisualizationSystem draws a DebugRegistry's sources and DebugLineBuffer is DebugLines.
            api(project(":awake:core:diagnostics"))
            api(project(":awake:core:spatial"))
            // LodGroup carries a LodPolicy, and selects through LodSelector.
            api(project(":awake:core:lod"))
            implementation(project(":awake:core:animation"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:world"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            api(project(":awake:engine:render:contract"))
            api(project(":awake:engine:render:passes"))
            api(project(":awake:engine:render:effects"))
            api(project(":awake:asset:terrain"))
            // The terrain surface seam: SceneTerrain stores a TerrainSurfaceReference and
            // TerrainContentSystem resolves it through TerrainSurfaceProviders.
            api(project(":awake:terrain"))
            // terrainContentFeature(TerrainComponent) adapts the pack's feature to the ECS
            // component. Acyclic: shader-pack knows nothing of the scene layer.
            api(project(":awake:asset:shader-pack"))
            // A scene terrain's surface carries a JsonElement payload, and TerrainContentSystem
            // takes the CoroutineScope its surface providers resolve in.
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
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

awakeSceneComponents {
    registry.set("com.awakekt.awake.scene.rendering.Scene3dSceneComponents")
}

mavenPublishing {
    pom {
        name.set("Awake Scene Rendering")
        description.set("Camera, lights, mesh renderers and the systems that turn a scene into draw calls")
    }
}

// The allocation probes here count every byte a frame's systems allocate. With escape analysis on,
// the JVM removes the short-lived temporaries Kotlin/Wasm really allocates, so a probe would pass over
// garbage the web build pays for (#1555).
tasks.named<Test>("desktopTest") {
    jvmArgs("-XX:-DoEscapeAnalysis")
}
