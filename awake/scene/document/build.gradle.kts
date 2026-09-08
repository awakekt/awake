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
    id("com.awakekt.awake.plugin.test-resources")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.scene.document"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:math"))
            api(project(":awake:core:logging"))
            api(project(":awake:core:host"))
            implementation(libs.kotlinx.serialization.json)
        }

        named("wasmJsMain") {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

awakeTestResources {
    roots.from(layout.projectDirectory.dir("src/commonTest/resources"))
}

mavenPublishing {
    pom {
        name.set("Awake Scene Document")
        description.set("Scene documents, asset library, and serialization format")
    }
}
