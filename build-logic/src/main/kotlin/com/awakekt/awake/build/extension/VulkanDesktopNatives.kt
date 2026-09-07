/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

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
fun JavaExec.wireVulkanDesktopNatives(bindingsProject: Project?) {
    if (bindingsProject == null || project.usePrebuiltVulkanNatives()) return
    dependsOn("${bindingsProject.path}:buildDesktopNative")
    val libDir = bindingsProject.layout.buildDirectory.dir("desktop-native-libs")
    jvmArgs("-Djava.library.path=${libDir.get().asFile.absolutePath}")
}

/**
 * Registers a standard desktop application execution task [taskName] for [mainClassName].
 */
fun Project.registerDesktopRunTask(
    mainClassName: String,
    taskName: String = "run",
    descriptionText: String = "Run the desktop application.",
): TaskProvider<JavaExec> {
    val bindingsProj = findProject(":awake:backend:vulkan:bindings")
    return tasks.register<JavaExec>(taskName) {
        group = "application"
        description = descriptionText
        dependsOn("desktopMainClasses")
        wireVulkanDesktopNatives(bindingsProj)
        useNagaShaderCompiler(this)
        mainClass.set(mainClassName)

        classpath = files(
            layout.buildDirectory.dir("classes/kotlin/desktop/main"),
            layout.buildDirectory.dir("processedResources/desktop/main"),
            provider {
                val kotlinExt = extensions.findByType(KotlinMultiplatformExtension::class.java)
                kotlinExt?.jvm("desktop")?.compilations?.findByName("main")?.runtimeDependencyFiles
            },
        )
        environment(VulkanDesktopEnv.environment())
        if (HostOs.isMac) {
            jvmArgs("-XstartOnFirstThread")
        }
    }
}
