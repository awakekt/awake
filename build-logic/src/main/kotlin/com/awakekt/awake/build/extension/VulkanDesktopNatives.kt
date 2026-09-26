/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

/**
 * Gets the runtime dependency configuration for the Kotlin Multiplatform desktop main compilation.
 *
 * [KotlinCompilation.runtimeDependencyFiles] is nullable. Wrapping a nullable lookup in a Gradle
 * provider can therefore create an absent provider, which fails later while Gradle is resolving
 * the run task's classpath dependencies. Resolve and validate the KMP model first, then return its
 * named runtime configuration as a normal Gradle configuration provider.
 */
internal fun Project.desktopRuntimeDependencyConfiguration(): NamedDomainObjectProvider<Configuration> {
    val kotlinExt = extensions.findByType(KotlinMultiplatformExtension::class.java)
        ?: throw GradleException(
            "Cannot configure the desktop run task for $path: the Kotlin Multiplatform plugin is not applied.",
        )
    val desktopMain = kotlinExt.targets.findByName("desktop")?.compilations?.findByName("main")
        ?: throw GradleException(
            "Cannot configure the desktop run task for $path: Kotlin target 'desktop' main compilation was not found.",
        )
    val runtimeConfigurationName = desktopMain.runtimeDependencyConfigurationName
        ?: throw GradleException(
            "Cannot configure the desktop run task for $path: Kotlin target 'desktop' main compilation " +
                "does not expose a runtime dependency configuration.",
        )

    return configurations.named(runtimeConfigurationName)
}

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
@Deprecated(
    "Unused; configure the run task with the application plugin's `awake { desktopApp { mainClass = ... } }` instead.",
)
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
            project.desktopRuntimeDependencyConfiguration(),
        )
        environment(VulkanDesktopEnv.environment())
        if (HostOs.isMac) {
            jvmArgs("-XstartOnFirstThread")
        }
    }
}
