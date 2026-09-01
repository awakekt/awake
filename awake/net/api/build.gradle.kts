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
        namespace = "io.github.awakelab.awake.net"
    }

    // No platform-specific code: every declaration is an interface or a plain buffer, so the
    // same source serves the Ktor WebSocket transport today and a UDP or WebTransport one
    // later (docs/plans/network.md, phases 3-4). Ktor must never appear as a dependency here
    // -- that is the whole point of the split.
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Net API")
        description.set("The transport facade: connections, delivery channels and packet buffers, with no transport attached")
    }
}
