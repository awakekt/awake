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
        namespace = "com.awakekt.awake.scene.particles"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":awake:particles"))
            api(project(":awake:core:math"))
            api(project(":awake:core:io"))
            api(project(":awake:ecs"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            api(project(":awake:engine:render:contract"))
            implementation(project(":awake:engine:render:passes"))
            implementation(project(":awake:core:geometry"))
            implementation(project(":awake:core:image"))
            implementation(project(":awake:core:logging"))
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":awake:engine:render:testing"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Particles")
        description.set("The particle_emitter scene component: its schema, binding, sprite loading and the systems that run emitters over a scene")
    }
}
