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
        namespace = "com.awakekt.awake.ai.behavior"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:ai"))
            api(project(":awake:core:math"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            api(project(":awake:navigation"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":awake:scene:runtime"))
            implementation(project(":awake:asset:terrain"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake AI Behavior")
        description.set("Navmesh-backed starter behaviors: Chase, Flee, Patrol")
    }
}
