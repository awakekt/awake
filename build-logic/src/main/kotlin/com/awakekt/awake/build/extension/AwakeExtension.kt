/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

/**
 * Desktop application runner DSL configuration spec.
 */
class AwakeDesktopAppSpec(private val project: Project) {
    /** Main class entrypoint (e.g. `"com.awakekt.awake.studio.app.MainKt"`). */
    var mainClass: String = ""

    /** Task name identifier (`"run"` by default). */
    var taskName: String = "run"

    /** Task description text. */
    var description: String = "Run the desktop application."

    internal fun configure() {
        if (mainClass.isBlank()) return
        val bindingsProj = project.findProject(":awake:backend:vulkan:bindings")
        project.tasks.register<JavaExec>(taskName) {
            group = "application"
            this.description = this@AwakeDesktopAppSpec.description
            dependsOn("desktopMainClasses")
            wireVulkanDesktopNatives(bindingsProj)
            useNagaShaderCompiler(this)
            mainClass.set(this@AwakeDesktopAppSpec.mainClass)

            classpath = project.files(
                project.layout.buildDirectory.dir("classes/kotlin/desktop/main"),
                project.layout.buildDirectory.dir("processedResources/desktop/main"),
                project.provider {
                    val kotlinExt = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
                    kotlinExt?.jvm("desktop")?.compilations?.findByName("main")?.runtimeDependencyFiles
                },
            )
            environment(VulkanDesktopEnv.environment())
            if (HostOs.isMac) {
                jvmArgs("-XstartOnFirstThread")
            }
        }
    }
}

/**
 * Desktop test execution DSL configuration spec.
 */
class AwakeTestSpec(private val project: Project) {
    /** Whether desktop tests require native Vulkan libraries. */
    var useVulkanNatives: Boolean = false

    /** Whether desktop tests require Naga shader compiler environment. */
    var useNagaShaders: Boolean = false

    /** Whether desktop tests enforce exclusive GPU execution lock. */
    var exclusiveGpu: Boolean = false

    /** Optional worker process fork threshold count. */
    var forkEvery: Long? = null

    internal fun configure() {
        val bindingsProj = project.findProject(":awake:backend:vulkan:bindings")
        val libDir = bindingsProj?.layout?.buildDirectory?.dir("desktop-native-libs")

        project.tasks.withType<Test>().configureEach {
            testLogging {
                events("passed", "skipped", "failed")
                showExceptions = true
                showStackTraces = true
                showCauses = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
            if (this@AwakeTestSpec.exclusiveGpu) {
                requireExclusiveGpu(this)
            }
            if (this@AwakeTestSpec.useVulkanNatives) {
                if (bindingsProj != null && libDir != null) {
                    dependsOn("${bindingsProj.path}:buildDesktopNative")
                    jvmArgs("-Djava.library.path=${libDir.get().asFile.absolutePath}")
                }
                environment(VulkanDesktopEnv.environment())
            }
            if (this@AwakeTestSpec.useNagaShaders) {
                useNagaShaderCompiler(this)
            }
            this@AwakeTestSpec.forkEvery?.let { forkCount ->
                this.forkEvery = forkCount
            }
        }
    }
}

/**
 * Test resources configuration spec.
 */
class AwakeTestResourcesSpec(private val project: Project) {
    /** Configures test resources roots. */
    fun roots(vararg paths: Any) {
        val ext = project.extensions.findByName("awakeTestResources") ?: return
        val rootsProp = ext::class.java.getMethod("getRoots").invoke(ext) as? ConfigurableFileCollection
        rootsProp?.from(*paths)
    }
}

/**
 * XCFramework export and native MoltenVK linking DSL configuration spec.
 */
class AwakeXcframeworkSpec(
    private val project: Project,
    val name: String,
) {
    /** Whether to link static MoltenVK framework and flags (`-lMoltenVK -lc++`). */
    var includeMoltenVK: Boolean = false

    internal fun configure() {
        val xcf = project.XCFramework(name)
        val kotlinExt = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return

        listOf("iosArm64", "iosSimulatorArm64").forEach { targetName ->
            kotlinExt.targets.findByName(targetName)?.let { target ->
                (target as? KotlinNativeTarget)?.binaries?.withType<Framework>()?.configureEach {
                    xcf.add(this)
                    if (includeMoltenVK) {
                        val opts = project.moltenVkLinkerOpts(targetName)
                        val frameworkName = this.name
                        linkerOpts(opts)
                        // Keep normal Gradle output quiet. The full linker list is useful when
                        // diagnosing an Apple link, but printing it at lifecycle level once per
                        // framework/configuration produces four noisy, path-heavy lines on every
                        // build. Use --info for the concise wiring record and --debug for options.
                        project.logger.info(
                            "MoltenVK | static | ${project.path} | $targetName | ${this.name}"
                        )
                        project.logger.debug(
                            buildString {
                                appendLine("MoltenVK linker configuration")
                                appendLine("  project:  ${project.path}")
                                appendLine("  target:   $targetName")
                                appendLine("  framework: $frameworkName")
                                appendLine("  options:")
                                opts.forEach { appendLine("    $it") }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unified `awake { ... }` extension for Awake Engine applications and samples.
 */
open class AwakeExtension(private val project: Project) {
    /** Configures the desktop application runner ("run" task). */
    fun desktopApp(action: Action<AwakeDesktopAppSpec>) {
        val spec = AwakeDesktopAppSpec(project).apply { action.execute(this) }
        spec.configure()
    }

    /** Configures desktop test execution parameters. */
    fun test(action: Action<AwakeTestSpec>) {
        val spec = AwakeTestSpec(project).apply { action.execute(this) }
        spec.configure()
    }

    /** Configures test resources merging. */
    fun testResources(action: Action<AwakeTestResourcesSpec>) {
        AwakeTestResourcesSpec(project).apply { action.execute(this) }
    }

    /** Configures XCFramework export and MoltenVK native linking. */
    fun xcframework(name: String, action: Action<AwakeXcframeworkSpec>) {
        val spec = AwakeXcframeworkSpec(project, name).apply { action.execute(this) }
        spec.configure()
    }
}

/**
 * Registers the `awake { ... }` extension on [Project].
 */
fun Project.awake(action: Action<AwakeExtension>) {
    val ext = extensions.findByType(AwakeExtension::class.java)
        ?: extensions.create("awake", AwakeExtension::class.java, this)
    action.execute(ext)
}
