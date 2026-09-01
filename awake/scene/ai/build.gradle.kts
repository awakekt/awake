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
        namespace = "io.github.awakelab.awake.scene.ai"
    }

    sourceSets {
        commonMain.dependencies {
            // Behaviours steer a Transform and ask navigation for a path. The arrow runs this way
            // and only this way: navigation knows nothing about behaviours, which is what lets a
            // game use pathfinding without linking any AI.
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:navigation"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
