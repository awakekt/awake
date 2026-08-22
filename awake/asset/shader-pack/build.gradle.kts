// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0

// Awake's shader "standard library": the authored WGSL every sample draws with, plus the Kotlin
// uniform layouts describing those specific shaders. Split out of awake:asset:shaders so that
// module holds only the backend-neutral contract (ShaderSet/ShaderStages/ShaderSource) -- a game
// shipping its own shaders depends on the contract and never pulls this content in. See
// docs/reference/render-extensibility.md for the authored-content-vs-capability rule this
// enforces structurally.
plugins {
    id("awake.shader-pipeline-convention")
    id("awake.kmp-library-convention")
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "io.github.ronjunevaldoz.awake.asset.shaderpack"
    }

    sourceSets {
        commonMain.dependencies {
            // UniformField/UniformLayout/GpuDataShape -- what the layouts here are built from.
            // api, not implementation: a consumer reading TexturedUniformLayout.total needs
            // those types visible through this module.
            api(project(":awake:engine:render:contract"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Shader Pack")
        description.set("Awake's authored WGSL shaders and their Kotlin-side uniform layouts")
    }
}

// The convention plugin defaults sourceDirectory to the non-standard src/commonMain/shaders --
// keep the shaders under the conventional KMP resources root instead, as awake:asset:shaders did
// before the split.
val packShaderDirectory = layout.projectDirectory.dir("src/commonMain/resources/shaders")

tasks.named<ValidateWgslShadersTask>("validateAwakeShaders") {
    sourceDirectory.set(packShaderDirectory)
}

tasks.named<SyncWgslShaderPipelineTask>("syncAwakeShaders") {
    sourceDirectory.set(packShaderDirectory)
}
