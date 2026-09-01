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
        namespace = "io.github.awakelab.awake.scene.navigation.grid"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:math"))
            // NavMesh, the seam this module implements, and the ECS types PathRequest uses.
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:world"))
            // Heightmap: a nav grid is baked from terrain, so the dependency runs this way and
            // :awake:asset:terrain stays unaware of navigation.
            api(project(":awake:asset:terrain"))
            // LineSegment, for navGridDebugLines. The contract module only, never a backend.
            api(project(":awake:engine:render:contract"))
            // PathRequestSystem runs its queries off the frame thread; it moved here
            // with the rest of the navigation contract.
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // NavGridCellStreamer is driven through the real WorldPartitionSystem, which needs a
            // scope; TestScope is what makes an off-thread bake assertable without a frame loop.
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Navigation")
        description.set("Heightmap-derived navigation grids, streamed navigation and path requests for agents")
    }
}
