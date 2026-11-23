/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("com.awakekt.awake.plugin.library")
    alias(libs.plugins.kotlin.serialization)
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.director"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:math"))
            api(project(":awake:core:camera"))
            api(project(":awake:core:color"))
            api(project(":awake:core:input"))
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.named<Test>("desktopTest") {
    // Allocation probes count every byte; disable escape analysis for deterministic measurements.
    jvmArgs("-XX:-DoEscapeAnalysis")
}

mavenPublishing {
    pom {
        name.set("Awake Director")
        description.set("Camera director, semantic framing, shot sequencer, lighting/audio/action choreography, and framing telemetry with no scene dependency")
    }
}
