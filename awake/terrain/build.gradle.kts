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
        namespace = "com.awakekt.awake.terrain"
    }

    sourceSets {
        commonMain.dependencies {
            // A resolved surface is a ShaderSet and the textures for its bindings.
            api(project(":awake:asset:shaders"))
            api(project(":awake:engine:render:contract"))
            // A surface reference carries its provider's payload as JSON.
            api(libs.kotlinx.serialization.json)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Terrain")
        description.set("The terrain surface seam: a surface reference, its provider, and the resolved shaders and textures")
    }
}
