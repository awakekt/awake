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
        namespace = "com.awakekt.awake.scene.audio"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:audio"))
            api(project(":awake:core:math"))
            api(project(":awake:ecs"))
            api(project(":awake:scene:scene-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Audio")
        description.set("ECS audio components and spatial audio system that bridge core audio to scene entities")
    }
}
