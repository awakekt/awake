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
        namespace = "com.awakekt.awake.scene.runtime"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:host"))
            implementation(project(":awake:core:input"))
            api(project(":awake:core:logging"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:world"))
            api(project(":awake:scene:rendering"))
            api(project(":awake:core:math"))
            api(project(":awake:core:audio"))
            api(project(":awake:scene:audio"))
            api(project(":awake:ecs"))
            api(project(":awake:engine:platform"))
            api(project(":awake:engine:compose"))
            api(project(":awake:core:text"))
            // The compose engine, which the overlay composes into.
            api(project(":awake:compose:ui"))
            api(project(":awake:engine:render:contract"))
            // The compose engine's locals, so the runtime can provide `World`/`Renderer`/stats to
            // an overlay instead of the overlay taking them as parameters.
            api(project(":awake:compose:runtime"))
            implementation(libs.kotlinx.serialization.json)
        }

        // SceneWriter's wasmJs actual hands the exported document to a browser download;
        // there is no filesystem to write to. Desktop needs nothing beyond java.io.
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
        name.set("Awake Scene Runtime")
        description.set("Scene documents, asset library, scheduling and the app lifecycle a scene runs inside")
    }
}
