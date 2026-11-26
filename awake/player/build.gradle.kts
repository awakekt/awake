/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("com.awakekt.awake.plugin.kmp-library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":awake:project:runtime"))
            api(project(":awake:engine:bootstrap"))
            api(project(":awake:engine:platform"))
            api(project(":awake:core:io"))
            api(project(":awake:core:input"))
            api(project(":awake:core:localization"))
            api(project(":awake:physics:api"))
            api(project(":awake:scene:authoring"))
            api(project(":awake:scene:runtime"))
            implementation(project(":awake:backend:jolt"))
            implementation(project(":awake:core:host"))
            implementation(project(":awake:ecs"))
            implementation(project(":awake:scene:binding"))
            implementation(project(":awake:scene:controls"))
            implementation(project(":awake:compose:ui"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Player")
        description.set("Standalone KMP runtime for playing Awake projects or single declarative scenes without the editor")
    }
}
