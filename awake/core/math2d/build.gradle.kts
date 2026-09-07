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
        namespace = "com.awakekt.awake.core.math2d"
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
