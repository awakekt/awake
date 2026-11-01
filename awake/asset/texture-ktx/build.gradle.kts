/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
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
    android { namespace = "com.awakekt.awake.asset.ktx" }
    sourceSets {
        // The project's texture cook settings, awake.textures.json.
        commonMain.dependencies { implementation(libs.kotlinx.serialization.json) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

mavenPublishing {
    pom {
        name.set("Awake KTX2 Textures")
        description.set("A pure-Kotlin reader for KTX2 texture containers: levels, encoding, colour space")
    }
}
