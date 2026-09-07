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
        namespace = "com.awakekt.awake.scene.core"
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
