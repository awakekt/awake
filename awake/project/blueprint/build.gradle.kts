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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Project Blueprints")
        description.set("Plays a project's blueprint graphs: the capability a project names to run its scene's blueprints")
    }
}
