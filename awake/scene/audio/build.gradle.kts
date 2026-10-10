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
        namespace = "com.awakekt.awake.scene.audio"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:audio"))
            api(project(":awake:core:math"))
            api(project(":awake:ecs"))
            api(project(":awake:scene:scene-core"))
            // `audio_source`: the scene schema, its binding, and the clips it names in the project's files.
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            api(project(":awake:core:io"))
            implementation(project(":awake:core:logging"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Audio")
        description.set("The audio_source scene component, and the ECS audio components and spatial audio system that bridge core audio to scene entities")
    }
}
