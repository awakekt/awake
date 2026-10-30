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
        namespace = "com.awakekt.awake.worldtext"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:math"))
            api(project(":awake:core:color"))
            api(project(":awake:core:text"))
            api(project(":awake:core:geometry"))
            api(project(":awake:engine:render:contract"))
            api(project(":awake:engine:render:passes"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":awake:asset:shader-dsl"))
            implementation(project(":awake:asset:shader-pack"))
            implementation(project(":awake:engine:render:testing"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake World Text")
        description.set("Text placed in a 3D world: laid out once into distance-field glyph quads, drawn in the scene pass, optionally facing the camera, with no scene dependency")
    }
}
