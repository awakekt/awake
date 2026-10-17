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
        namespace = "com.awakekt.awake.ai"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:ecs"))
            // Blackboards hold positions as Vec3f, and snapshots store them.
            api(project(":awake:core:math"))
            // Node and state parameters are read into editor forms from their serializers.
            api(project(":awake:core:schema"))
            // The node catalogue and tree graphs a visual editor edits.
            api(project(":awake:node-graph"))
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake AI")
        description.set("Behavior trees and state machines for game AI, built in Kotlin or loaded from JSON through a node registry")
    }
}
