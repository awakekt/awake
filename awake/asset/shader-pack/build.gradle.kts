/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// Awake's shader "standard library": the authored WGSL every sample draws with, plus the Kotlin
// uniform layouts describing those specific shaders. Split out of awake:asset:shaders so that
// module holds only the backend-neutral contract (ShaderSet/ShaderStages/ShaderSource) -- a game
// shipping its own shaders depends on the contract and never pulls this content in. See
// docs/reference/render-extensibility.md for the authored-content-vs-capability rule this
// enforces structurally.
plugins {
    id("awake.kmp-library-convention")
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.asset.shaderpack"
    }

    sourceSets {
        commonMain.dependencies {
            // UniformField/UniformLayout/GpuDataShape -- what the layouts here are built from.
            // api, not implementation: a consumer reading TexturedUniformLayout.total needs
            // those types visible through this module.
            api(project(":awake:engine:render:contract"))
            // ShaderSet/ShaderStages appear in skyboxContentFeature's signature.
            api(project(":awake:asset:shaders"))
            // ContentFeature/SkyboxRenderFeature: the shipped content declaration lives beside
            // its own .wgsl here, recording lives in render:passes. Acyclic -- passes does not
            // depend on this module.
            api(project(":awake:engine:render:passes"))
        }
        commonMain.dependencies {
            // The shadow shaders' ASL definitions live HERE, beside the .wgsl they emit,
            // deriving their struct from MaterialUniformLayouts.LitShadow via fieldsFrom.
            // Exposed (not implementation) because the definitions are public values whose
            // type, AslShaderDefinition, comes from the DSL module.
            api(project(":awake:asset:shader-dsl"))
            // terrainContentFeature ships the clipmap geometry and heightmap beside its shader.
            // Acyclic: :awake:asset:terrain depends only on core modules.
            api(project(":awake:asset:terrain"))
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
