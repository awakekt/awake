/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("awake.kmp-library-convention")
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.compose.di"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:compose:runtime"))
            api(project(":awake:core:di"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Compose DI")
        description.set("Dependency Injection bridge for Awake Compose runtime and CompositionLocals")
    }
}
