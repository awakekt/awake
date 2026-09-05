/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("awake.kmp-library-convention")
    alias(libs.plugins.kotlin.serialization)
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    id("awake.test-resources-convention")
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.scene.document"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:math"))
            api(project(":awake:ecs"))
            api(project(":awake:core:logging"))
            api(project(":awake:core:host"))
            api(project(":awake:scene:scene-core"))
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
