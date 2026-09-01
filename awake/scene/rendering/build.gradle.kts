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
        namespace = "io.github.awakelab.awake.scene.rendering"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math"))
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:animation"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:world"))
            api(project(":awake:engine:render:contract"))
            api(project(":awake:asset:terrain"))
            // terrainContentFeature(TerrainComponent) adapts the pack's feature to the ECS
            // component. Acyclic: shader-pack knows nothing of the scene layer.
            api(project(":awake:asset:shader-pack"))
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
