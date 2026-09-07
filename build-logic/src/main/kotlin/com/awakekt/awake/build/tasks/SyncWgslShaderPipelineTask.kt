/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.gradle.work.DisableCachingByDefault

/**
 * Places the canonical WGSL where each backend's loader looks for it.
 *
 * Both backends now consume WGSL: WebGPU feeds it to the device directly, and Vulkan compiles it
 * through the in-process naga binding (`VulkanShaderResolver`), which caches per path. This task
 * therefore compiles nothing -- `validateAwakeShaders` is where naga still runs, and it only has
 * to say yes or no.
 */
@DisableCachingByDefault(because = "Syncs WGSL shader sources into build directory")
abstract class SyncWgslShaderPipelineTask : DefaultTask() {

    @get:Optional
    @get:IgnoreEmptyDirectories
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    /** Extra read-only roots layered on top of [sourceDirectory] -- empty by default, so every
     * module that never sets this keeps today's exact single-directory behavior. Each entry's
     * own `.wgsl` files are walked and synced alongside [sourceDirectory]'s, keeping ITS OWN
     * root for `relativeTo(...)` (so a shared module's subdirectory structure, if any, is
     * preserved the same way [sourceDirectory]'s own is) -- see the plugin doc comment for why
     * this exists (deduplicating WGSL shared verbatim across sample modules). A filename that
     * exists under more than one root throws rather than silently picking one, so a stray
     * leftover copy in a consumer's own directory is caught immediately instead of shadowing
     * the shared canonical file. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val additionalSourceDirectories: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val webGpuOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val vulkanOutputDirectory: DirectoryProperty

    @TaskAction
    fun sync() {
        val sourceRoot = sourceDirectory.orNull?.asFile
        if (sourceRoot == null || !sourceRoot.exists()) {
            logger.lifecycle("No WGSL shader source directory found; skipping.")
            return
        }
        val wgslFiles = collectWgslFiles(sourceRoot, additionalSourceDirectories.files)
        if (wgslFiles.isEmpty()) {
            logger.lifecycle("No WGSL shader sources found at ${sourceRoot.invariantSeparatorsPath}; skipping.")
            return
        }

        val webGpuRoot = webGpuOutputDirectory.asFile.get().also(File::mkdirs)
        val vulkanRoot = vulkanOutputDirectory.asFile.get().also(File::mkdirs)
        val expectedWebGpu = mutableSetOf<String>()
        val expectedVulkan = mutableSetOf<String>()

        wgslFiles.forEach { (sourceFile, root) ->
            val relativePath = sourceFile.relativeTo(root).invariantSeparatorsPath

            val webGpuTarget = File(webGpuRoot, relativePath)
            webGpuTarget.parentFile.mkdirs()
            sourceFile.copyTo(webGpuTarget, overwrite = true)
            expectedWebGpu += webGpuTarget.canonicalPath

            // WGSL, not SPIR-V: VulkanShaderResolver compiles a .wgsl resource through the
            // in-process naga binding and caches the result, so shipping source costs one
            // compile per shader at load and removes a build artifact per stage. It also
            // collapses the two naga copies this repo used to keep in version lockstep by hand
            // (the CLI here and the awake-naga-bridge crate) down to the one that renders.
            val vulkanTarget = File(vulkanRoot, relativePath)
            vulkanTarget.parentFile.mkdirs()
            sourceFile.copyTo(vulkanTarget, overwrite = true)
            expectedVulkan += vulkanTarget.canonicalPath
        }

        pruneStaleOutputs(webGpuRoot, expectedWebGpu, "wgsl")
        pruneStaleOutputs(vulkanRoot, expectedVulkan, "wgsl")
        // Leftovers from when this task emitted SPIR-V; without this a stale pair sits in the
        // resource tree forever, and the loader would still find it.
        pruneStaleOutputs(vulkanRoot, emptySet(), "spv")
    }

    private fun pruneStaleOutputs(root: File, expectedPaths: Set<String>, extension: String) {
        if (!root.exists()) return
        root.walkTopDown()
            .filter { it.isFile && it.extension == extension && it.canonicalPath !in expectedPaths }
            .forEach(File::delete)
    }

}
