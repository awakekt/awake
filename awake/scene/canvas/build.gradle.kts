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
        namespace = "com.awakekt.awake.scene.canvas"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:ecs"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            api(project(":awake:compose:runtime"))
            api(project(":awake:compose:ui"))
            implementation(project(":awake:compose:foundation"))
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:text"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Canvas")
        description.set("Screen-anchored game UI (text, panels, bars, buttons) stored in scenes and drawn over the game")
    }
}
