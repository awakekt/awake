/*
 * Awake
 * Awake.awake-engine-platform
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
    id("awake.kmp-library-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "io.github.ronjunevaldoz.awake.engine.platform"
    }

    // GraphicsEngine.kt: the backend-neutral render bootstrap that VulkanEngine
    // (awake-backend-vulkan) and WebGpuEngine (awake-backend-webgpu) both extend, plus the
    // AppLifecycle interface an app implements and injects into it -- deliberately has zero
    // ECS/scene-graph/UI knowledge (see docs/reference/decision-log.md, "GameApplication a
    // standalone render bootstrap").
    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:math"))
            implementation(project(":awake:core:host"))
            implementation(project(":awake:core:image"))
            implementation(project(":awake:core:input"))
            // Application, FixedTimestepLoop (via awake-base transitively).
            // Renderer/LineSegment -- AppLifecycle.ready(renderer)'s parameter type and
            // drawDebugLines()'s plumbing, nothing scene/ECS-specific.
            api(project(":awake:engine:render:contract"))
            implementation(libs.kotlinx.coroutines.core)
        }
        named("androidMain") {
            dependencies {
                implementation(project(":awake:ui:ui-core"))
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
