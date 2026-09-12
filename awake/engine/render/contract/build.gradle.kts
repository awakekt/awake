/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
/*
 * Awake
 * Awake.awake-engine-render-api
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


plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.render"
    }

    // No platform-specific code at all in this module (see docs/mvp-plan.md's module
    // restructuring notes) -- every declaration here is a plain interface/data class,
    // implemented by each backend module (awake-vulkan today; a future awake-backend-webgpu
    // once slice 2 physically splits that out).
    sourceSets {
        commonMain.dependencies {
            api(project(":awake:core:graphics2d"))
            api(project(":awake:core:text"))
            implementation(project(":awake:core:color"))
            // DrawCall/Renderer.draw() take Mat4/Camera (portable math), and the resource-
            // loading `expect fun`s some backends' Texture implementations need come from
            // awake-base too.
            implementation(project(":awake:core:math"))
            // VertexFormat/MeshGeometry/GpuDataShape live here now -- api, not implementation:
            // DrawCall.mesh.format and Renderer.createMesh(MeshGeometry) put these types in this
            // module's own public signatures, so a consumer needs them visible. See
            // docs/reference/module-architecture.md -- the "prefer implementation" rule's
            // deliberate exception.
            api(project(":awake:core:geometry"))
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // PipelineRegistry.register is suspend (shader loading is IO), so its tests need a
            // multiplatform coroutine runner -- runBlocking does not exist on wasmJs.
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Render Contract")
        description.set("The renderer-facing surface -- Renderer, Mesh, Material, RenderTarget -- that backends implement and engine code depends on")
    }
}
