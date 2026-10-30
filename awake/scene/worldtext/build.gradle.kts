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
        namespace = "com.awakekt.awake.scene.worldtext"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":awake:worldtext"))
            api(project(":awake:core:math"))
            api(project(":awake:ecs"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            api(project(":awake:scene:scene3d"))
            api(project(":awake:engine:render:contract"))
            implementation(project(":awake:engine:render:passes"))
            implementation(project(":awake:core:schema"))
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

awakeSceneComponents {
    registry.set("com.awakekt.awake.scene.worldtext.WorldTextSceneComponents")
}

mavenPublishing {
    pom {
        name.set("Awake Scene World Text")
        description.set("The world_text scene component: its schema, binding and the scene feature that draws text placed in a scene")
    }
}
