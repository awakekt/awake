/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// A bounded-outbox WebSocket client Transport over a Ktor HttpClient. Kept out of awake:net:api,
// which carries no HTTP or socket library. The host builds its own HttpClient (or uses the default
// ServiceLoader-backed client) and picks its engine per target (CIO or OkHttp on desktop/Android,
// Darwin on iOS, Js in a browser); this module names none in main sources.
plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.net.ktor"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:net:api"))
            api(libs.ktor.client.core)
            api(libs.ktor.client.websockets)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("desktopTest") {
            dependencies {
                implementation(project(":awake:net:server-ktor"))
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.client.cio)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Net Client Ktor")
        description.set("Bounded-outbox WebSocket client transport over a Ktor HttpClient")
    }
}
