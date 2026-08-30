/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
/*
 * Awake
 * Awake.awake-asset-shaders
 *
 * Copyright (c) ronjunevaldoz 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// The backend-neutral shader contract only: ShaderSet/ShaderStages/ShaderSource and the helpers
// that resolve them. The authored WGSL and its uniform layouts moved to awake:asset:shader-pack,
// so a game shipping its own shaders can depend on this module without pulling that content in.
plugins {
    id("awake.kmp-library-convention")
    id("awake.render-extensibility-convention")
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.asset.shaders"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:host"))
            // UniformField/UniformLayout/GpuDataShape. api, not implementation: a consumer
            // building its own ShaderSet needs those types visible through this module
            // (matches awake:backend:vulkan's own api(render:contract) for the same reason).
            api(project(":awake:engine:render:contract"))
            // ContentFeature, for RenderPlan's own field. Acyclic: passes depends on
            // render:contract and the core modules, never on this one.
            api(project(":awake:engine:render:passes"))
            // The engine's own UI shaders are ASL definitions here rather than .wgsl resources,
            // so EngineShaderSets can hand a backend their WGSL with no file to ship. Acyclic:
            // shader-dsl depends on core:geometry and render:contract only.
            api(project(":awake:asset:shader-dsl"))
            // readResourceBytes -- ShaderSource.resolveBytes()'s own implementation detail, not
            // part of this module's public API surface, so implementation (not api) is enough.
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Asset Shaders")
        description.set("Backend-neutral shader contract: ShaderSet, ShaderStages, ShaderSource")
    }
}
