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
        namespace = "com.awakekt.awake.scene.ai"
    }

    sourceSets {
        commonMain.dependencies {
            // The behaviours these components describe, and the AgentPlacement the scene answers.
            api(project(":awake:ai:behavior"))
            // registerAiBehaviors registers the navigation component with the behaviours.
            api(project(":awake:scene:navigation"))
            // TransformAgentPlacement places agents by Transform; references resolve by Name.
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":awake:scene:runtime"))
            implementation(project(":awake:asset:terrain"))
        }
        named("desktopTest") {
            dependencies {
                // The docs samples show the scene DSL beside the scene document.
                implementation(project(":awake:scene:authoring"))
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene AI")
        description.set("The patrol, chase and flee scene components: their schemas, bindings, and the Transform placement the behaviours move by")
    }
}
