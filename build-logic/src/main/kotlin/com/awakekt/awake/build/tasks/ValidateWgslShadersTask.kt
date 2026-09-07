/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Validation task with no outputs")
abstract class ValidateWgslShadersTask : DefaultTask() {

    @get:Optional
    @get:IgnoreEmptyDirectories
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    /** See [SyncWgslShaderPipelineTask]'s own doc comment on its identically-named property --
     * same extra-roots/duplicate-filename-throws behavior, shared via [collectWgslFiles]. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val additionalSourceDirectories: ConfigurableFileCollection

    @get:Input
    abstract val nagaExecutable: Property<String>

    @TaskAction
    fun validate() {
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

        wgslFiles.forEach { (file, _) -> runNagaValidation(file) }
    }

    private fun runNagaValidation(input: File) {
        val executable = nagaExecutable.get()
        val process = try {
            ProcessBuilder(
                executable,
                input.absolutePath
            )
                .directory(project.projectDir)
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            logger.warn("naga executable '$executable' not found on PATH; skipping WGSL validation for ${input.name}.")
            return
        }

        val outputText = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException(
                buildString {
                    appendLine("naga validation failed for ${input.invariantSeparatorsPath}.")
                    append(outputText.ifBlank { "No compiler output." })
                }
            )
        }
    }
}
