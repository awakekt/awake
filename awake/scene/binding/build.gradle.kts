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
    id("com.awakekt.awake.plugin.test-resources")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.scene.binding"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:ecs"))
            api(project(":awake:core:logging"))
            api(project(":awake:scene:document"))
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

awakeTestResources {
    roots.from(layout.projectDirectory.dir("src/commonTest/resources"))
}

mavenPublishing {
    pom {
        name.set("Awake Scene Binding")
        description.set("ECS component bindings, resolvers, and world adapters for Awake scene documents")
    }
}
