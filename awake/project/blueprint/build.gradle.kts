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
        namespace = "com.awakekt.awake.project.blueprint"
    }

    sourceSets {
        commonMain.dependencies {
            // A capability a project opts into, so the project runtime itself knows nothing of blueprints.
            api(project(":awake:project:runtime"))
            api(project(":awake:scene:blueprint"))
            // A Load Scene naming a scene the project doesn't have warns on the blueprint log as it loads.
            implementation(project(":awake:core:logging"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            // A character walking into a sensor needs a real physics world.
            implementation(project(":awake:backend:jolt"))
            // A host that draws, so the canvas nodes, which only change what is shown, run, and a
            // renderer for a project runtime plays, as Load Scene's does; this one draws nothing.
            implementation(project(":awake:engine:render:testing"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Project Blueprints")
        description.set("Plays a project's blueprint graphs: the capability a project names to run its scene's blueprints")
    }
}
