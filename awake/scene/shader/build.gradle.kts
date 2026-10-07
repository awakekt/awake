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
        namespace = "com.awakekt.awake.scene.shader"
    }

    sourceSets {
        commonMain.dependencies {
            // The documents an effect draws, and the content-feature host it attaches them to.
            api(project(":awake:asset:shader-document"))
            api(project(":awake:asset:shaders"))
            api(project(":awake:engine:render:contract"))
            api(project(":awake:core:io"))
            api(project(":awake:ecs"))
            // The node's Transform places a plane.
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:document"))
            api(project(":awake:scene:binding"))
            implementation(project(":awake:core:math"))
            // A texture's image is an ordinary PNG.
            implementation(project(":awake:core:image"))
            implementation(project(":awake:core:logging"))
            api(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// ShaderEffectDocsSampleTest reads the guide's snippets, so an edit to one must rerun the test.
tasks.named<Test>("desktopTest") {
    val snippets = listOf("gradient-sky.shader.json", "shader-effect.scene.json")
        .map { rootProject.file("website/docs/snippets/rendering/$it") }
    inputs.files(snippets)
        .withPropertyName("shaderEffectSnippets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

mavenPublishing {
    pom {
        name.set("Awake Scene Shader")
        description.set("The shader_effect scene component: shader documents a project ships, loaded, attached and run over a scene")
    }
}
