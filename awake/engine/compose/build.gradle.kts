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
        namespace = "com.awakekt.awake.engine.compose"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:engine:platform"))
            api(project(":awake:compose:ui"))
            api(project(":awake:core:text"))
        }
        commonTest.dependencies {
            implementation(project(":awake:engine:render:testing"))
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Engine Compose")
        description.set("Hosts the Compose runtime inside an engine frame, and paints its output through the renderer")
    }
}
