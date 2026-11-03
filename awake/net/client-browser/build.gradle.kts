/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// A browser WebSocket client for a game built for wasmJs, with no HTTP or socket library: the
// browser's own `WebSocket`, in a dedicated Web Worker so a busy frame can't delay it, or on the page.
// The public connect call is wasmJs only; the connection it hands back is common code, so desktopTest
// checks its framing, arrival lag and backpressure, and the wasm files are only the browser calls.
// The worker's script ships in the klib under awake-net/, and the `socket-worker` Gradle plugin puts
// it beside a consumer's page.
plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.net.browser"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:net:api"))
            implementation(libs.kotlinx.coroutines.core)
            // Says once, by name, when the socket worker can't run and the socket falls back to the page.
            implementation(project(":awake:core:logging"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Net Client Browser")
        description.set("A browser WebSocket client transport that runs its socket in a dedicated Web Worker, or on the page")
    }
}
