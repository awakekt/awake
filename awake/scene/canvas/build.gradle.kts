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
    id("com.awakekt.awake.plugin.scene-components")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.scene.canvas"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:ui:canvas"))
            api(project(":awake:ecs"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            implementation(project(":awake:scene:scene-core"))
            api(project(":awake:compose:runtime"))
            api(project(":awake:compose:ui"))
            api(project(":awake:core:io"))
            implementation(project(":awake:core:logging"))
            api(project(":awake:core:math"))
            api(project(":awake:core:math2d"))
            api(project(":awake:core:color"))
            api(project(":awake:core:text"))
            api(project(":awake:core:localization"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":awake:compose:ui-testing"))
            implementation(project(":awake:compose:foundation"))
        }
    }
}

awakeSceneComponents {
    registry.set("com.awakekt.awake.scene.canvas.CanvasSceneComponents")
}

mavenPublishing {
    pom {
        name.set("Awake Scene Canvas")
        description.set("The canvas scene components: the schemas, validation and World bindings for awake:ui:canvas")
    }
}

tasks.named<Test>("desktopTest") {
    // The forked test JVM does not see Gradle's own system properties; forward the switch that
    // re-records component baselines.
    System.getProperty("AWAKE_RECORD_SNAPSHOTS")
        ?.let { systemProperty("AWAKE_RECORD_SNAPSHOTS", it) }
    // The allocation probes count every byte. With escape analysis on, C2 scalar-replaces short-lived
    // objects such as a class reference made for one read, hiding garbage Android's runtime, which
    // has no escape analysis, does make (#1169), and moving the count between JVMs (#945).
    jvmArgs("-XX:-DoEscapeAnalysis")
}
