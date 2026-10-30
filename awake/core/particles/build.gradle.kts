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
        namespace = "com.awakekt.awake.core.particles"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:math"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        desktopTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Core Particles")
        description.set(
            "Pure structure-of-arrays particle simulation: emission shapes, curves, bursts, spin, turbulence and ground bounce, with no ECS or renderer dependency",
        )
    }
}
