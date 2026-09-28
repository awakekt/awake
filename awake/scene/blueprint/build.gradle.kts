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
        namespace = "com.awakekt.awake.scene.blueprint"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:blueprint"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            api(project(":awake:scene:physics"))
            implementation(project(":awake:scene:scene3d"))
            implementation(project(":awake:core:animation"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":awake:scene:runtime"))
        }
        // Desktop only: the door test runs real Jolt, and jolt-jni ships no library for an Android host.
        desktopTest.dependencies {
            implementation(project(":awake:backend:jolt"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Blueprint")
        description.set("Runs blueprints on scene entities: the blueprint component, BlueprintSystem and engine nodes")
    }
}
