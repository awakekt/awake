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
        namespace = "com.awakekt.awake.compose.ui"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:graphics2d"))
            api(project(":awake:core:math2d"))
            implementation(project(":awake:core:math"))
            api(project(":awake:core:color"))
            api(project(":awake:core:input"))
            api(project(":awake:compose:runtime"))
            // Primitive layers only: nothing here reaches a UI engine above this module.
            api(project(":awake:core:text"))
            // Platform image decoders for decodeImageBitmap.
            implementation(project(":awake:core:image"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // Test-only, and deliberately not in commonMain: `padding`, `size` and `background` are
            // foundation's, as in Compose, but the chain, paint and pointer tests that live here
            // need something concrete to build a tree out of. Keeping the tests here also keeps
            // this module's coverage its own.
            implementation(project(":awake:compose:foundation"))
        }
    }
}

tasks.named<Test>("desktopTest") {
    // The allocation tests count every byte. With escape analysis on, C2 scalar-replaces short-lived
    // objects, hiding garbage Android's runtime makes (#1169); the canvas module sets the same flag.
    jvmArgs("-XX:-DoEscapeAnalysis")
}

mavenPublishing {
    pom {
        name.set("Awake Compose UI")
        description.set("Modifiers, layout nodes, input dispatch and semantics for the engine's Compose implementation")
    }
}
