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
 * Rejects `Map` and `Set` declarations keyed by a `data class` with mutable (`var`) primary
 * constructor properties (#1709).
 *
 * Mutating a `var` property in a `data class` changes `hashCode()` and `equals()` after insertion,
 * stranding entries in old hash buckets so lookups miss, `getOrPut` duplicates entries, and removal
 * silently leaks memory. Pre-existing compatibility sites live in [baseline], which only shrinks.
 */
@DisableCachingByDefault(because = "Verification task with no outputs")
abstract class VerifyMutableMapKeysTask : DefaultTask() {
    /** The repository root, which reported paths are taken relative to. */
    @get:Input
    abstract val rootPath: Property<String>

    /** The checked-in `<path> <count>` list of pre-existing compatibility sites. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baseline: RegularFileProperty

    /** The Kotlin files in each module's main source sets. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainSources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val root = File(rootPath.get())
        val files = mainSources.files.filter { it.isFile && it.extension == "kt" }
        val sourcesByPath = files.associate { file ->
            file.relativeTo(root).invariantSeparatorsPath to file.readText()
        }
        val mutableDataClasses = mutableDataClassNames(sourcesByPath.values)
        val sites = sortedMapOf<String, List<Pair<Int, String>>>()
        sourcesByPath.forEach { (path, source) ->
            mutableMapKeySites(source, mutableDataClasses).takeIf { it.isNotEmpty() }?.let { sites[path] = it }
        }
        val allowed = parseMutableMapKeyBaseline(baseline.get().asFile.readText())
        val failures = mutableMapKeyFailures(sites, allowed)
        if (failures.isNotEmpty()) {
            throw GradleException(
                "Mutable map-key check failed (${failures.size} problem(s)):\n" + failures.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle(
            "Mutable map-key check passed: ${sites.values.sumOf { it.size }} baselined site(s) across ${mutableDataClasses.size} mutable data classes",
        )
    }
}
