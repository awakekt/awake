/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec

/**
 * Whether `-Pawake.prebuiltNatives=<dir>` was passed for this build.
 *
 * That property already makes `awake/backend/vulkan/bindings`'s `desktopMain` add the dir as a
 * resources root, and `VulkanNativeLoader` falls back to extracting the classpath resource when
 * `java.library.path` is empty. Consumers use this to skip depending on `buildDesktopNative`
 * (slow CMake configure+build) and skip pointing `java.library.path` at its output.
 */
fun Project.usePrebuiltVulkanNatives(): Boolean =
    (findProperty("awake.prebuiltNatives") as String?)?.isNotBlank() == true

/**
 * Wires [bindingsProject]'s desktop Vulkan native lib into this `run` task.
 *
 * Building from source is the default: a host can only build its own platform, so that is the
 * honest local-dev default. [usePrebuiltVulkanNatives] opts out of it entirely -- no
 * `buildDesktopNative` dependency, no `java.library.path` -- relying on the classpath-resource
 * fallback instead.
 *
 * `desktopTest` tasks should keep depending on `buildDesktopNative` unconditionally: they are
 * what validates the native code, not just a consumer of it, so this helper is `run`-only.
 */
fun JavaExec.wireVulkanDesktopNatives(bindingsProject: Project) {
    if (project.usePrebuiltVulkanNatives()) return
    dependsOn("${bindingsProject.path}:buildDesktopNative")
    val libDir = bindingsProject.layout.buildDirectory.dir("desktop-native-libs")
    jvmArgs("-Djava.library.path=${libDir.get().asFile.absolutePath}")
}
