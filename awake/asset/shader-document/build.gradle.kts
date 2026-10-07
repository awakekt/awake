/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// Shader documents: shaders as project data. A restricted, serializable subset of ASL that a published
// project ships and Core checks, compiles to WGSL and draws as a content feature. No scene dependency:
// awake:scene:shader binds it into scenes.
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
        namespace = "com.awakekt.awake.asset.shaderdocument"
    }

    sourceSets {
        commonMain.dependencies {
            // The document lowers to ASL and is emitted by its WGSL emitter.
            api(project(":awake:asset:shader-dsl"))
            // ShaderSet and ContentFeatureSource: a compiled document is drawn as a content feature.
            api(project(":awake:asset:shaders"))
            api(project(":awake:engine:render:contract"))
            api(project(":awake:engine:render:passes"))
            api(project(":awake:core:geometry"))
            api(project(":awake:core:math"))
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// ShaderDocumentReferenceDocsTest reads these files, so an edit to either must rerun the test.
tasks.named<Test>("desktopTest") {
    val pages = listOf("docs/reference/shader-document.md", "mkdocs.yml").map { rootProject.file("website/$it") }
    inputs.files(pages)
        .withPropertyName("shaderDocumentReference")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

mavenPublishing {
    pom {
        name.set("Awake Shader Document")
        description.set("Shaders as project data: a checked, serializable subset of ASL compiled to WGSL and drawn as a content feature")
    }
}
