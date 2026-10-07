/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("com.awakekt.awake.plugin.library")
    alias(libs.plugins.kotlin.serialization)
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    android { namespace = "com.awakekt.awake.asset.sprite" }
    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:animation"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Sprite Assets")
        description.set("Portable sprite-sheet metadata import and named frame clips")
    }
}
