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
    id("com.awakekt.awake.plugin.scene-components")
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
            // NavigationAgentSystem steers an agent through its MovementControl and reads its Transform.
            api(project(":awake:scene:controls"))
            api(project(":awake:scene:scene-core"))
            implementation(project(":awake:core:math"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

awakeSceneComponents {
    registry.set("com.awakekt.awake.scene.navigation.NavigationSceneComponents")
}

mavenPublishing {
    pom {
        name.set("Awake Scene Navigation")
        description.set("The navigation scene components: the walkable grid a scene document carries, agents that walk it, and their bindings")
    }
}
