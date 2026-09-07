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
        namespace = "com.awakekt.awake.core.host"
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Browser fetch()/ImageBitmap, and the coroutine `await` that turns their Promises
        // into suspend calls. wasmJs-only -- see the note in awake:core's own history.
        named("wasmJsMain") {
            dependencies {
                implementation(libs.kotlinx.browser)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Core Host")
        description.set("Host platform services: frame loop, resource bytes")
    }
}
