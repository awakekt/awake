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
        namespace = "com.awakekt.awake.world"
    }

    sourceSets {
        commonMain.dependencies {
            // A cell's content is applied to an ECS World on the frame thread.
            api(project(":awake:ecs"))
            // CompositeCellStreamListener loads its listeners' cells concurrently.
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
        name.set("Awake World")
        description.set("World cells: coordinates, partition radii and the cell streaming contract, with no scene dependency")
    }
}
