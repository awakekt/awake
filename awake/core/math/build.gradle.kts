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
        namespace = "io.github.ronjunevaldoz.awake.core.math"
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: Vec4.pixelCoords and Lens.projectToViewport return a
            // Vec2 in their own signatures, so every caller needs the type visible.
            api(project(":awake:core:math2d"))
        }

        // Otherwise no dependencies, deliberately. These types are the vocabulary every other module
        // speaks, so anything this module depends on becomes a dependency of the whole engine.
        // A new file here that needs a logger or a coroutine belongs somewhere else.
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Core Math")
        description.set("Vectors, matrices, quaternions, rays, frustums, and camera lenses -- no dependencies")
    }
}
