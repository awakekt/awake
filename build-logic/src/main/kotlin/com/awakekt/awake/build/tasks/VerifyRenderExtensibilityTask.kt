/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Rejects authored shader content inside a module meant to hold only the backend-neutral shader
 * contract.
 *
 * `awake:asset:shaders` once held both, and both backends `api()` it -- so every consumer
 * transitively saw Awake's own shaders and their uniform layouts, whether or not it used them.
 * The content moved to `awake:asset:shader-pack`; this keeps it from drifting back, which
 * nothing else can catch (a `.wgsl` file or a `*UniformLayout.kt` compiles fine wherever it
 * lives). See docs/reference/render-extensibility.md.
 */
@DisableCachingByDefault(because = "Verification task with no outputs")
abstract class VerifyRenderExtensibilityTask : DefaultTask() {

    @get:Input
    abstract val modulePath: Property<String>

    /** Where authored content belongs instead, named in the failure so the fix is obvious. */
    @get:Input
    abstract val contentModulePath: Property<String>

    /** Filename suffixes that mark a file as authored shader content rather than contract. */
    @get:Input
    abstract val forbiddenFileSuffixes: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val suffixes = forbiddenFileSuffixes.get()
        val violations = sourceFiles.files
            .filter { file -> suffixes.any { file.name.endsWith(it) } }
            .map { it.invariantSeparatorsPath }
            .sorted()

        if (violations.isEmpty()) return

        throw GradleException(
            buildString {
                appendLine("${modulePath.get()} holds the backend-neutral shader contract only, but found authored shader content:")
                violations.forEach { appendLine("  $it") }
                appendLine()
                appendLine("Move it to ${contentModulePath.get()}. Both backends api() the contract module, so content")
                appendLine("placed here reaches every consumer whether it uses those shaders or not.")
                append("See docs/reference/render-extensibility.md.")
            },
        )
    }
}
