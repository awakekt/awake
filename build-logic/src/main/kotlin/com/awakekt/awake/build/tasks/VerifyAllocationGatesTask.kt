/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Keeps every new allocation gate on `readAllocation`, the median of several windows (#1592).
 *
 * A test that reads the calling thread's allocated bytes itself, and is not in [recordedReaders], fails;
 * so does a recorded file that no longer reads them. See [readsAllocationCounter].
 */
@DisableCachingByDefault(because = "Verification task with no outputs")
abstract class VerifyAllocationGatesTask : DefaultTask() {
    /** The repository root, which reported paths are taken relative to. */
    @get:Input
    abstract val rootPath: Property<String>

    /** The Kotlin files in each module's test source sets. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSources: ConfigurableFileCollection

    /** The files that read the counter themselves when the helper arrived, one repository path a line. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val recordedReaders: RegularFileProperty

    @TaskAction
    fun verify() {
        val root = File(rootPath.get())
        val readers = testSources.files
            .filter { it.isFile && it.extension == "kt" && readsAllocationCounter(it.readText()) }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSet()
        val allowed = recordedReaders.get().asFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        val failures = allocationGateFailures(readers, allowed)
        if (failures.isNotEmpty()) {
            throw GradleException("Allocation gates failed (${failures.size}):\n" + failures.joinToString("\n") { "  $it" })
        }
        logger.lifecycle("Allocation gates passed: ${readers.size} recorded files read the counter themselves; every other gate uses readAllocation")
    }
}
