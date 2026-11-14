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
}

kotlin {
    android {
        namespace = "com.awakekt.awake.core.settings"
    }

    sourceSets {
        commonMain.dependencies {
            // api: a key reuses the launch option's value type and names the option that overrides it.
            api(project(":awake:core:config"))
            // api: FileSettingsStorage takes a FileSystem, and every key is observed as a StateFlow.
            api(project(":awake:core:io"))
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Core Settings")
        description.set("Typed, persisted player settings with launch-option overrides for Kotlin Multiplatform")
    }
}
