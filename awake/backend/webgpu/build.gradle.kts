/*
 * Awake
 * Awake.awake-backend-webgpu
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


import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.backend-layering-convention")
    id("awake.spotless-convention")
}

kotlin {
    // 25, not the repo-wide 17: wgpu4k-jvm ships bytecode built for JVM target 25, and Kotlin
    // refuses to inline a 25-target inline function into a 17-target caller. Contained to this
    // module -- its desktop target is test-only (nothing consumes its desktop output), and the
    // wasmJs target this backend actually ships is unaffected. Auto-provisioned via the foojay
    // resolver in settings.gradle.kts.
    jvmToolchain(25)

    // Module restructuring slice 2 (see docs/mvp-plan.md): physically split out of
    // awake-vulkan (now awake-backend-vulkan). wasmJs is the only target this backend SHIPS to
    // -- production runs Vulkan on desktop/Android (decision-log D26).
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    // Desktop is a TEST target, not a shipping one. wgpu4k publishes a jvm variant and
    // wgpu4k-native-jvm bundles prebuilt libwgpu_native binaries, so this backend can run
    // headless on the JVM and reuse the pixel-baseline harness -- WebGPU was otherwise
    // compile-checked and never pixel-checked. It exercises Awake's WebGPU code path over
    // wgpu-native, NOT a browser's WebGPU implementation, so canvas sizing, JS interop and
    // browser driver quirks still need a browser check.
    jvm("desktop")

    // GLFW (which wgpu4k's desktop context bootstrap uses) refuses to initialise off the
    // process's first thread on macOS. Harmless elsewhere -- the flag only exists on the macOS
    // JVM, so it is applied per-host rather than unconditionally.
    if (System.getProperty("os.name").startsWith("Mac")) {
        tasks.withType<Test>().configureEach {
            jvmArgs(
                "-XstartOnFirstThread",
                // wgpu4k reaches the CAMetalLayer through Rococoa, whose cglib needs to
                // defineClass into java.lang -- JPMS blocks that by default on a modern JDK.
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            )
        }
    }

    // detekt's compiler frontend tops out at JVM target 22, and this module's toolchain is 25
    // (see jvmToolchain above). The target only affects detekt's own analysis, not the bytecode
    // this module produces, so pinning it back is safe.
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "22"
    }
    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        jvmTarget = "22"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:engine:render:passes2d"))
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:host"))
            implementation(project(":awake:core:input"))
            implementation(project(":awake:core:math"))
            // WebGPU consumes the raw UI render stream, not headless/design-system widgets.
            // Declare its core and value-contract dependencies directly instead of inheriting
            // them through render-contract.
            implementation(project(":awake:ui:ui-core"))
            // Mesh/Material/Renderer implement the same narrow backend-neutral interfaces
            // awake-backend-vulkan's do -- see awake-engine-render-api's module doc.
            api(project(":awake:engine:render:contract"))
            // The shared render-pass layer (SharedOpaqueRenderFeature + the CommandRecorder
            // port). `api`, not `implementation`: this module's public pipeline/mesh/material
            // types implement the port's handle interfaces, so consumers see them.
            api(project(":awake:engine:render:passes"))
            // ShaderSet/ShaderStages/ShaderSource -- api, not implementation: WebGpuEngine's own
            // public constructor takes ShaderSet params, so a consumer needs the type visible
            // too (matches awake-backend-vulkan's identical api(awake:asset:shaders) dependency).
            api(project(":awake:asset:shaders"))
            implementation(project(":awake:scene"))
            implementation(libs.kotlinx.coroutines.core)
            // Multiplatform: the same wgpu4k API backs both wasmJs and the desktop test target.
            // Only the toolkit (canvas bootstrap) and kotlinx-browser stay wasm-only.
            implementation(libs.wgpu4k)
            implementation(libs.webgpu.ktypes.descriptors)
            // Toolkit, not just core: `WGPUContext` is the device/surface bundle GraphicsDevice
            // takes, and it is published for BOTH wasmJs and jvm. Only the bootstrap that
            // produces one differs -- canvasContextRenderer() on wasm, glfwContextRenderer() on
            // desktop -- so no abstraction over it is needed here.
            implementation(libs.wgpu4k.toolkit)
            // WebGpuGameApplication now extends GameApplication (see
            // docs/reference/decision-log.md for the duplication this replaces). `api`,
            // not `implementation`: same reasoning as awake-backend-vulkan's identical
            // dependency -- it's a supertype, so consumers need it on their classpath too.
            api(project(":awake:engine:platform"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        named("wasmJsMain") {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
