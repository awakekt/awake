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
        namespace = "com.awakekt.awake.particles"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:math"))
            api(project(":awake:ecs"))
            api(project(":awake:engine:render:contract"))
            api(project(":awake:engine:render:passes"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Particles")
        description.set("Particle emitters: simulation, ground bounce, burst scheduling and instanced draw packets, with no scene dependency")
    }
}
