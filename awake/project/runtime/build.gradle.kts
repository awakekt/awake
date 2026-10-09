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
        namespace = "com.awakekt.awake.project.runtime"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:project"))
            api(project(":awake:scene:authoring"))
            api(project(":awake:scene:gltf"))
            api(project(":awake:scene:character"))
            // Patrol, chase and flee, and the grid they route over, which a played scene carries as data.
            implementation(project(":awake:ai:behavior"))
            implementation(project(":awake:scene:ai"))
            api(project(":awake:core:geometry"))
            api(project(":awake:core:io"))
            implementation(project(":awake:asset:shader-pack"))
            implementation(project(":awake:core:animation"))
            implementation(project(":awake:core:math"))
            implementation(project(":awake:particles"))
            implementation(project(":awake:scene:particles"))
            // CoreSceneContent.ShaderEffects names its ShaderEffectAssets.
            api(project(":awake:scene:shader"))
            // paged_terrain: the scene binding, the residency it streams, and the layered surface an index can name.
            implementation(project(":awake:scene:worldstream"))
            implementation(project(":awake:terrain"))
            implementation(project(":awake:kit:terrain-layers"))
            implementation(project(":awake:core:logging"))
            // A SceneCapability lists the bindings of the components it adds, so their type is in this API.
            api(project(":awake:scene:binding"))
            // Names the components a scene uses that no capability registers.
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":awake:engine:render:testing"))
            implementation(project(":awake:backend:jolt"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Project Runtime")
        description.set("Plays an Awake project without the editor, running the systems its scene's components call for")
    }
}
