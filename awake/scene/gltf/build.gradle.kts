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
        namespace = "com.awakekt.awake.scene.gltf"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:scene:runtime"))
            api(project(":awake:asset:gltf"))
            api(project(":awake:core:color"))
            api(project(":awake:core:geometry"))
            api(project(":awake:core:io"))
            implementation(project(":awake:asset:shader-pack"))
            implementation(project(":awake:core:host"))
            implementation(project(":awake:core:image"))
            implementation(project(":awake:core:math"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene glTF")
        description.set("Resolves .gltf and .glb model paths in scenes to meshes and materials")
    }
}
