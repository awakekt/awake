/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("awake.kmp-library-convention")
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.core.image"
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
        name.set("Awake Core Image")
        description.set("Bitmap decode and RGBA8 pixel buffers")
    }
}
