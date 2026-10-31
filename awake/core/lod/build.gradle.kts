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
        namespace = "com.awakekt.awake.core.lod"
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Core LOD")
        description.set("Level-of-detail selection: screen coverage or distance thresholds, hysteresis, per-entity policy and global bias")
    }
}
