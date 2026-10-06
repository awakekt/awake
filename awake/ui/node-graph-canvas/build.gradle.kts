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
    id("com.awakekt.awake.plugin.ui-ownership")
    id("com.awakekt.awake.plugin.ui-authored-units")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.ui.nodegraph"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:node-graph"))
            api(project(":awake:compose:foundation"))
            implementation(project(":awake:core:color"))
        }
        commonTest.dependencies {
            implementation(project(":awake:compose:ui-testing"))
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Node Graph Canvas")
        description.set("Pan-and-zoom node graph editing surface built on Awake Compose")
    }
}

// Forward -DAWAKE_RECORD_SNAPSHOTS=true to the test JVM, which records visual baselines.
tasks.named<Test>("desktopTest") {
    System.getProperty("AWAKE_RECORD_SNAPSHOTS")?.let { systemProperty("AWAKE_RECORD_SNAPSHOTS", it) }
    testLogging {
        // Preserve the measured windows and ceiling in CI logs, including the assertion message.
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
