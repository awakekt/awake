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
        namespace = "com.awakekt.awake.trail"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:math"))
            api(project(":awake:core:color"))
            api(project(":awake:core:geometry"))
            api(project(":awake:engine:render:contract"))
            api(project(":awake:engine:render:passes"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":awake:engine:render:testing"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Trail")
        description.set("Trails: strips of point pairs whose vertices and colours change every frame, rewritten in place in one GPU buffer, with no scene dependency")
    }
}
