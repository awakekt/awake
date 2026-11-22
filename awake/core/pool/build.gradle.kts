/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.awakekt.awake.plugin.kmp-library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    sourceSets {
        // SortedLongSet's buffer and sort: multiplatform, and already a Core dependency.
        commonMain.dependencies {
            implementation(libs.androidx.collection)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Core Pool")
        description.set("Frame-scoped object pools and reusable id sets that allocate nothing in steady state")
    }
}
