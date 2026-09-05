/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("awake.kmp-library-convention")
    alias(libs.plugins.kotlin.serialization)
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.ai.behavior"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:ai"))
            api(project(":awake:core:math"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:document"))
            api(project(":awake:navigation"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
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
