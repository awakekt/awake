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
        namespace = "com.awakekt.awake.ai.behavior"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:ai"))
            api(project(":awake:core:math"))
            // The behaviours are components and their systems run over a World.
            api(project(":awake:ecs"))
            // A behaviour asks for its routes through a PathRequest.
            api(project(":awake:navigation"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // NavGridChaseIntegrationTest bakes its grid from a heightmap.
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
