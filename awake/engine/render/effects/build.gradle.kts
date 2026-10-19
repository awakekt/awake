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
        namespace = "com.awakekt.awake.render.effects"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:engine:render:contract"))
            api(project(":awake:core:color"))
            api(project(":awake:core:geometry"))
            api(project(":awake:core:graphics2d"))
            api(project(":awake:core:math"))
            implementation(project(":awake:asset:shader-dsl"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Render Effects")
        description.set("Shared GPU image effects lowered to backend-neutral ImagePassChains")
    }
}
