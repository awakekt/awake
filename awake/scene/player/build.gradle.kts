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
        namespace = "com.awakekt.awake.scene.player"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:scene:authoring"))
            api(project(":awake:scene:gltf"))
            api(project(":awake:project"))
            api(project(":awake:core:geometry"))
            api(project(":awake:core:io"))
            implementation(project(":awake:asset:shader-pack"))
            implementation(project(":awake:compose:foundation"))
            implementation(project(":awake:core:animation"))
            implementation(project(":awake:core:math"))
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
        name.set("Awake Scene Player")
        description.set("Plays an Awake project without the editor, with the built-in assets its scenes name")
    }
}
