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
        namespace = "com.awakekt.awake.scene.character"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:scene:physics"))
            api(project(":awake:scene:controls"))
            implementation(project(":awake:core:math"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":awake:scene:runtime"))
            implementation(project(":awake:backend:jolt"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Character")
        description.set("A walking, jumping character moved by the physics character controller, saved in scenes")
    }
}
