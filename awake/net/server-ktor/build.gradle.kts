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
        namespace = "com.awakekt.awake.net.server.ktor"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:net:api"))
            api(project(":awake:ecs"))
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("desktopMain") {
            dependencies {
                api(libs.ktor.server.core)
                api(libs.ktor.server.websockets)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("desktopTest") {
            dependencies {
                implementation(libs.ktor.server.test.host)
                implementation(libs.ktor.client.websockets)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Net Server Ktor")
        description.set("Ktor WebSocket transport plugin and authoritative game-server host with feature API")
    }
}
