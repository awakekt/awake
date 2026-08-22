// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("awake.kmp-library-convention")
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "io.github.ronjunevaldoz.awake.core.math2d"
    }

    sourceSets {
        // No dependencies. Screen-space 2D primitives are vocabulary for the UI stack, the 2D
        // draw path and both backends -- anything this module depends on becomes theirs too.
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Core Math 2D")
        description.set("Screen-space 2D primitives: Vec2, Rectangle, Size2D -- no dependencies")
    }
}
