/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
/*
 * Awake
 * Awake.awake-engine-render-passes
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
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.render.passes"
    }

    // Backend-neutral by construction: nothing here may reference a Vulkan or WebGPU type (see
    // README.md). Same no-platform-code shape as :awake:engine:render:contract next door.
    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:color"))
            // api, not implementation: the shared render-feature classes this module will hold
            // take contract types (DrawCall, VertexFormat, Material) in their own public
            // signatures, so both backends need them visible transitively.
            api(project(":awake:core:math"))
            api(project(":awake:engine:render:contract"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Render Passes")
        description.set("Render features and pipelines: shadow, opaque, transparent, skybox and content passes")
    }
}
