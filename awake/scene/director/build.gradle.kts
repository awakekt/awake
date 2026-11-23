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
        namespace = "com.awakekt.awake.scene.director"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:director"))
            api(project(":awake:core:math"))
            api(project(":awake:core:camera"))
            api(project(":awake:core:audio"))
            api(project(":awake:core:input"))
            api(project(":awake:core:io"))
            implementation(project(":awake:core:animation"))
            api(project(":awake:ecs"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            api(project(":awake:scene:scene3d"))
            api(project(":awake:scene:controls"))
            api(project(":awake:scene:audio"))
            api(project(":awake:particles"))
            api(project(":awake:scene:particles"))
            api(project(":awake:engine:render:effects"))
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":awake:scene:runtime"))
        }
    }
}

awakeSceneComponents {
    registry.set("com.awakekt.awake.scene.director.DirectorSceneComponents")
}

mavenPublishing {
    pom {
        name.set("Awake Scene Director")
        description.set("Scene components, ECS bindings, world stage queries, input replay bridge, and DirectorSystem wiring :awake:director over a scene")
    }
}
