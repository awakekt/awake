/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// A ready-made ContentTransport over a Ktor HttpClient, for a host that already has one. Kept out of
// awake:project, which carries no HTTP library: a game that plays a project with its own client, or
// none, never takes Ktor. The host builds the client and so picks its engine (OkHttp or Android on
// Android, Darwin on iOS, CIO or Java on desktop, Js in a browser); this module names none.
plugins {
    id("com.awakekt.awake.plugin.library")
    // Tests encode a project index, as a publisher would.
    alias(libs.plugins.kotlin.serialization)
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.project.transport.ktor"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:project"))
            // The constructor takes the host's HttpClient.
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(project(":awake:core:io"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Project Transport Ktor")
        description.set("A ContentTransport over a Ktor HttpClient, for reading published and protected project content")
    }
}
