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
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.awakekt.awake.compose.runtime"
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.named<Test>("desktopTest") {
    // The allocation probes count every byte. With escape analysis on, C2 scalar-replaces short-lived
    // objects, so what a probe sees depends on when the compile lands; the canvas and compose modules
    // set the same flag (#1521).
    jvmArgs("-XX:-DoEscapeAnalysis")
}

mavenPublishing {
    pom {
        name.set("Awake Compose Runtime")
        description.set("The Composer, composition locals and remember/state primitives the engine's UI is built from")
    }
}
