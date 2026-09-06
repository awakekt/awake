/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("awake.kmp-library-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    id("awake.ui-ownership-convention")
    id("awake.ui-authored-units-convention")
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
