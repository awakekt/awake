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
}

kotlin {
    android {
        namespace = "com.awakekt.awake.tailwind"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:math"))
            implementation(project(":awake:core:color"))
            api(project(":awake:compose:foundation"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
