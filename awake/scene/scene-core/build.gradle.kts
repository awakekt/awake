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
        namespace = "io.github.awakelab.awake.scene.core"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:math"))
            api(project(":awake:ecs"))
            // WorldPartitionSystem schedules cell loads off the frame thread; see
            // docs/tasks/2026-08-29-async-cell-streaming-plan.md. The only dependency this
            // otherwise-lean module carries beyond math and the ECS.
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Core")
        description.set("Transforms, names, world-cell streaming and the systems every scene runs")
    }
}
