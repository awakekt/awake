/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("awake.kmp-library-convention")
    alias(libs.plugins.kotlin.serialization)
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.scene.authoring"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:input"))
            // Depends on the specific scene leaf modules it actually uses, not the
            // `:awake:scene` facade -- see docs/tasks/2026-08-05-scene-module-split-proposal.md
            // Phase 5. `TransformSystem` lives in `:awake:scene:scene-core`,
            // `PlayerControlSystem` lives in this module directly (it needs `ui-core`'s
            // `UiInputOwnership`, which `:awake:scene:controls` deliberately stays free of).
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:rendering"))
            api(project(":awake:scene:controls"))
            api(project(":awake:scene:runtime"))
            api(project(":awake:engine:bootstrap"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(project(":awake:core:math"))
            implementation(project(":awake:core:text"))
            implementation(project(":awake:compose:foundation"))
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Authoring")
        description.set("The scene and application DSL: entities, systems, assets and UI declared in Kotlin")
    }
}
