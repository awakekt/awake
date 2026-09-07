/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
    id("com.awakekt.awake.plugin.ui-ownership")
    id("com.awakekt.awake.plugin.ui-authored-units")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.ui.material3"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:color"))
            api(project(":awake:compose:foundation"))
        }
        commonTest.dependencies {
            implementation(project(":awake:compose:ui-testing"))
            implementation(kotlin("test"))
        }
    }
}
