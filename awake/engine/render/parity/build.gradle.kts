/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.extension.*
plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    // 25, not the repo-wide 17: this module links the WebGPU backend, whose wgpu4k-jvm dependency
    // ships bytecode built for JVM 25. Vulkan's own classes are older and load fine here --
    // forward, not backward.
    jvmToolchain(25)

    android {
        namespace = "com.awakekt.awake.render.parity"
    }

    // detekt's compiler frontend tops out at JVM target 22 and this module's toolchain is 25. The
    // target only affects detekt's own analysis, not the bytecode produced here.
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { jvmTarget = "22" }
    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach { jvmTarget = "22" }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                api(project(":awake:engine:render:contract"))
                api(project(":awake:backend:vulkan"))
                api(project(":awake:backend:webgpu"))
                implementation(project(":awake:engine:render:passes"))
                implementation(project(":awake:engine:render:passes2d"))
                implementation(project(":awake:engine:render:testing"))
                implementation(project(":awake:asset:shaders"))
                implementation(project(":awake:asset:shader-pack"))
                implementation(project(":awake:asset:shader-compiler"))
                implementation(project(":awake:core:color"))
                implementation(project(":awake:core:geometry"))
                implementation(project(":awake:core:graphics2d"))
                implementation(project(":awake:core:host"))
                implementation(project(":awake:core:text"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// The same loader environment the Vulkan backend's own headless tests need: MoltenVK's ICD and
// validation layers live in the bindings module's native output, and without them
// `vkCreateInstance` fails with VK_ERROR_LAYER_NOT_PRESENT rather than with anything that reads
// like a missing setup step. The task dependency is the load-bearing half -- a path pointed at a
// directory nothing has built yet is silently a no-op.
val desktopVulkanEnv = VulkanDesktopEnv.environment()
val desktopNativeLibDir =
    project(":awake:backend:vulkan:bindings").layout.buildDirectory.dir("desktop-native-libs")

tasks.named<Test>("desktopTest") {
    if (HostOs.isMac) {
        // GLFW, which wgpu4k's desktop bootstrap uses to get an adapter, refuses to initialise off
        // the process's first thread. Vulkan runs windowless here and does not care, so one process
        // can hold both backends.
        jvmArgs(
            "-XstartOnFirstThread",
            // wgpu4k reaches the CAMetalLayer through Rococoa, whose cglib defines classes into
            // java.lang -- JPMS blocks that by default on a modern JDK.
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        )
    }
    // Both backends open a real device in one process; two of these running at once is how a GPU
    // test starts failing for reasons unrelated to the change under test.
    requireExclusiveGpu(this)
    dependsOn(":awake:backend:vulkan:bindings:buildDesktopNative")
    // The Vulkan fixture compiles the shipped WGSL to SPIR-V through the same naga binding the
    // runtime resolver uses.
    useNagaShaderCompiler(this)
    jvmArgs("-Djava.library.path=${desktopNativeLibDir.get().asFile.absolutePath}")
    environment(desktopVulkanEnv)
    // `-DAWAKE_RECORD_SNAPSHOTS=true` on the Gradle CLI only sets the property on Gradle's own
    // JVM; the test JVM needs it forwarded explicitly -- same wiring as the Vulkan backend's.
    System.getProperty("AWAKE_RECORD_SNAPSHOTS")
        ?.let { systemProperty("AWAKE_RECORD_SNAPSHOTS", it) }
}
