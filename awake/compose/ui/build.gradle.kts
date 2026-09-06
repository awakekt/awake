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
            // Primitive layers only -- none carries a :awake:ui:ui-core dependency in commonMain,
            // so nothing here reaches the immediate-mode engine this module replaces.
            api(project(":awake:core:text"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // Test-only, and deliberately not in commonMain: `padding`, `size` and `background` are
            // foundation's, as in Compose, but the chain, paint and pointer tests that live here
            // need something concrete to build a tree out of. The same shape `:awake:ui:graphics`
            // uses with `ui-core`. Keeping the tests here also keeps this module's coverage its own.
            implementation(project(":awake:compose:foundation"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Compose UI")
        description.set("Modifiers, layout nodes, input dispatch and semantics for the engine's Compose implementation")
    }
}
