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
        namespace = "com.awakekt.awake.scene.scene2d"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":awake:ecs"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene 2D")
        description.set("The 2D scene components, starting with sprite: their schema, binding and the systems that run them over a scene")
    }
}
