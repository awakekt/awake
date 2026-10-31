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
    android { namespace = "com.awakekt.awake.asset.meshlod" }
    sourceSets {
        commonMain.dependencies {
            // The cook reads each primitive's vertex streams through the glTF importer.
            implementation(project(":awake:asset:gltf"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Mesh LOD")
        description.set("Attribute-aware mesh simplification and cook-time glTF MSFT_lod level generation")
    }
}
