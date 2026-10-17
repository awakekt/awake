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
    android {
        namespace = "com.awakekt.awake.ui.canvas"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:compose:runtime"))
            api(project(":awake:compose:ui"))
            api(project(":awake:core:math2d"))
            api(project(":awake:core:math"))
            api(project(":awake:core:text"))
            api(project(":awake:core:schema"))
            api(project(":awake:core:localization"))
            // A canvas's touch controls show by the device last used, which callers pass in.
            api(project(":awake:core:input"))
            api(libs.kotlinx.serialization.json)
            implementation(project(":awake:compose:foundation"))
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:logging"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":awake:compose:ui-testing"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake UI Canvas")
        description.set("Screen-anchored game UI (text, panels, bars, buttons) drawn over the game, with no scene or ECS dependency")
    }
}

tasks.named<Test>("desktopTest") {
    // The forked test JVM does not see Gradle's own system properties; forward the switch that
    // re-records component baselines.
    System.getProperty("AWAKE_RECORD_SNAPSHOTS")
        ?.let { systemProperty("AWAKE_RECORD_SNAPSHOTS", it) }
}
