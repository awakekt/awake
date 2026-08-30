/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("awake.kmp-library-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.scene.physics"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:math"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:physics:api"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
