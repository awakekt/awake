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
    alias(libs.plugins.kover)
    id("com.awakekt.awake.plugin.test-resources")
}

val generatorFonts = rootProject.layout.projectDirectory.dir("awake/ui/font-atlas-generator/fonts")

kotlin {
    android {
        namespace = "com.awakekt.awake.compose.testing"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:compose:foundation"))
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:engine:render:testing"))
            implementation(project(":awake:core:text"))
        }
        commonTest {
            // The Roboto faces LoadedFontRasterTest loads at run time.
            resources.srcDir(generatorFonts)
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":awake:core:host"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

awakeTestResources {
    roots.from(generatorFonts)
}

tasks.named<Test>("desktopTest") {
    // The forked test JVM does not see Gradle's own system properties; forward the switch that
    // re-records component baselines, as ui:shadcn does.
    System.getProperty("AWAKE_RECORD_SNAPSHOTS")
        ?.let { systemProperty("AWAKE_RECORD_SNAPSHOTS", it) }
}
