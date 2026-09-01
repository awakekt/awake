/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// Phase 0/1 walking skeleton for docs/plans/network.md: server-authoritative movement over a
// Ktor WebSocket transport, with a binary tick codec. The protocol, codec and client are
// common; only the server (a browser can host nothing) and the two entry points are
// platform-specific.
// Keep in sync with .claude/launch.json and the port table in docs/reference/developer-docs.md.
val wasmDevPort = 8088

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    jvmToolchain(17)
    applyDefaultHierarchyTemplate()

    jvm("desktop") {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        mainRun { mainClass.set("io.github.awakelab.awake.sample.netdemo.MainKt") }
    }

    // Fixed dev-server port so this sample does not collide with the others (webpack defaults
    // every sample to 8080). Keep in sync with .claude/launch.json and the port table in
    // docs/reference/developer-docs.md.
    @Suppress("OPT_IN_USAGE")
    wasmJs {
        browser {
            commonWebpackConfig {
                devServer = devServer?.copy(port = wasmDevPort)
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:net:api"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        named("desktopMain").dependencies {
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.network.tls.certificates)
        }
        named("desktopTest").dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        named("wasmJsMain").dependencies {
            // Browser WebSocket API; a page cannot open a raw socket of any other kind.
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.browser)
        }
    }
}
