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
        namespace = "com.awakekt.awake.blueprint"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:node-graph"))
            api(project(":awake:ecs"))
            // A node that cannot do what it is wired to do says so once.
            implementation(project(":awake:core:logging"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Blueprint")
        description.set("Runtime for event-driven game logic authored as node graphs")
    }
}
