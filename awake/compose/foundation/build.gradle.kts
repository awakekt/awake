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
}

kotlin {
    android {
        namespace = "com.awakekt.awake.compose.foundation"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:compose:ui"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        named("desktopTest") {
            resources.srcDir(rootProject.file("tools/fonts-tooling/layout"))
            resources.srcDir(rootProject.file("awake/ui/font-atlas-generator/fonts"))
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

tasks.named<Test>("desktopTest") {
    systemProperty("awake.layout.report", layout.buildDirectory.dir("reports/text-layout-conformance").get().asFile.absolutePath)
}

mavenPublishing {
    pom {
        name.set("Awake Compose Foundation")
        description.set("Row, Column, Box, scrolling and the layout modifiers built on the Compose UI node tree")
    }
}
