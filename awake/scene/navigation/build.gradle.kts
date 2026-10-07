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
        namespace = "com.awakekt.awake.scene.navigation"
    }

    sourceSets {
        commonMain.dependencies {
            // NavigationGrid holds the NavGrid a scene's `navigation` component bakes.
            api(project(":awake:navigation"))
            api(project(":awake:ecs"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Navigation")
        description.set("The navigation scene component: the walkable grid a scene document carries, and its binding")
    }
}
