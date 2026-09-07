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
        namespace = "com.awakekt.awake.render.passes2d"
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
