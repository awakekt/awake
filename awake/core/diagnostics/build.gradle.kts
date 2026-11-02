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
        namespace = "com.awakekt.awake.core.diagnostics"
    }

    sourceSets {
        commonMain.dependencies {
            // Vec3f and Color appear in DebugLines' own public signature.
            api(project(":awake:core:math"))
            api(project(":awake:core:color"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Core Diagnostics")
        description.set("Debug sources: readings and world-space lines a game shows about itself, turned on by one flag")
    }
}
