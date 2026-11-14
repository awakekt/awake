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
        namespace = "com.awakekt.awake.core.diagnostics"
    }

    sourceSets {
        commonMain.dependencies {
            // Vec3f and Color appear in DebugLines' own public signature.
            api(project(":awake:core:math"))
            api(project(":awake:core:color"))
            // StatsSummary and CrashSession carry @Serializable descriptors in their public signatures.
            api(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.test)
        }
        // LocalStorageCrashStorage and the page lifecycle hooks: wasmJs-only, as awake:core:host's browser access.
        named("wasmJsMain") {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Core Diagnostics")
        description.set("Debug sources: readings and world-space lines a game shows about itself, turned on by one flag")
    }
}
