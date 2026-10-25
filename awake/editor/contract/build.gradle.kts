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
        namespace = "com.awakekt.awake.editor.contract"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:math"))
            api(project(":awake:core:color"))
            api(project(":awake:core:input"))
            api(project(":awake:core:state"))
            api(project(":awake:core:di"))
            api(project(":awake:compose:runtime"))
            api(project(":awake:compose:ui"))
            api(project(":awake:core:schema"))
            api(project(":awake:ecs"))
            // Plugin manifests, archives and their trust formats; re-exported for the deprecated aliases.
            api(project(":awake:plugin-archive"))
            implementation(project(":awake:core:io"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.bignum)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Editor Contract")
        description.set("Public extension point and plugin contracts for Awake editor hosts")
    }
}
