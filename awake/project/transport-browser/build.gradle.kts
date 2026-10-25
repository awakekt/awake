/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// A ready-made ContentTransport over the browser's own fetch(), for a game built for wasmJs, with no
// HTTP library at all. Kept out of awake:project like the Ktor transport. The public transport is
// wasmJs only; the request and response mapping it delegates to is common code, so desktopTest
// checks it, and the wasm file is only the fetch() call.
plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.project.transport.browser"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:project"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        named("wasmJsMain") {
            dependencies {
                // ArrayBuffer and typed arrays, for the response body.
                implementation(libs.kotlinx.browser)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Project Transport Browser")
        description.set("A ContentTransport over the browser's fetch(), for reading published and protected project content on the web")
    }
}
