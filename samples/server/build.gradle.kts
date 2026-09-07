/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// A small, protocol-agnostic Ktor WebSocket debug-control server -- see
// DebugControlServer.kt's own doc comment. No kotlinx.serialization/JSON dependency here on
// purpose: this module sends/receives raw text frames only, generic over whatever command/
// response types a consumer (e.g. samples:hello-cube) parses/encodes itself.
plugins {
    alias(libs.plugins.kotlin.jvm)
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    testImplementation(kotlin("test"))
}
