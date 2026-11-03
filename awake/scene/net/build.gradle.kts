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
        namespace = "com.awakekt.awake.scene.net"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:net:prediction"))
            api(project(":awake:scene:scene-core"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

awakeSceneComponents {
    registry.set("com.awakekt.awake.scene.net.NetSceneComponents")
}

mavenPublishing {
    pom {
        name.set("Awake Scene Net")
        description.set("The predicted_entity scene component and ECS Transform binding for client prediction")
    }
}
