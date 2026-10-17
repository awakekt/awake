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
    id("com.awakekt.awake.plugin.test-resources")
}

// Roboto, which has Cyrillic but no Hangul, for the locale font tests.
val generatorFonts = rootProject.layout.projectDirectory.dir("awake/ui/font-atlas-generator/fonts")

kotlin {
    android {
        namespace = "com.awakekt.awake.core.localization"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:io"))
            api(project(":awake:core:input"))
            api(project(":awake:core:text"))
            api(libs.kotlinx.coroutines.core)
            implementation(project(":awake:core:logging"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest {
            resources.srcDir(generatorFonts)
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":awake:core:host"))
            }
        }
    }
}

awakeTestResources {
    roots.from(generatorFonts)
}

mavenPublishing {
    pom {
        name.set("Awake Core Localization")
        description.set("Per-locale string tables, ICU-style messages with CLDR plurals, and the active locale")
    }
}
