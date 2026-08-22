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
        namespace = "io.github.ronjunevaldoz.awake.render.passes2d"
    }

    sourceSets {
        commonMain.dependencies {
            // RenderFeature/RenderPassSlot/RenderFrameContext still live in :render:passes, which
            // becomes passes3d once the 2D half has left. If that split happens, hoist the three
            // dispatch types into :render:contract and drop this edge -- they are neutral.
            api(project(":awake:engine:render:passes"))
            api(project(":awake:engine:render:contract"))
            api(project(":awake:core:math"))

            api(project(":awake:core:graphics2d"))

            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:geometry"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Render Passes 2D")
        description.set("Backend-neutral 2D pass logic: run coalescing, mesh upload, command recording")
    }
}
