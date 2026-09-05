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
        namespace = "io.github.awakelab.awake.scene.controls"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:input"))
            api(project(":awake:scene:scene-core"))
            implementation(project(":awake:core:math"))
            api(project(":awake:scene:rendering"))
            api(project(":awake:compose:ui"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Controls")
        description.set("Camera rigs and input-driven controls for orbiting, flying and walking a scene")
    }
}
