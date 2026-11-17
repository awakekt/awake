/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Keeps every ECS query's block a lambda at the call, so the inline query inlines it.
 *
 * `World.queryEach` and `Family.forEach` are inline so the `Entity` each visit receives stays unboxed.
 * Handed a function kept in a field, they call its generic `invoke` instead, which boxes every entity
 * on every frame: a cost Android pays and a warmed desktop JVM hides, so an allocation test cannot
 * hold it (#1742). [functionObjectQueryLines] finds such a call; there is no baseline, so any fails.
 */
@DisableCachingByDefault(because = "Verification task with no outputs")
abstract class VerifyInlinedQueriesTask : DefaultTask() {
    /** The repository root, which reported paths are taken relative to. */
    @get:Input
    abstract val rootPath: Property<String>

    /** The Kotlin files in each module's main source sets. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainSources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val root = File(rootPath.get())
        val sites = sortedMapOf<String, List<Int>>()
        mainSources.files
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = functionObjectQueryLines(file.readText())
                if (lines.isNotEmpty()) sites[file.relativeTo(root).invariantSeparatorsPath] = lines
            }
        val failures = inlinedQueryFailures(sites)
        if (failures.isNotEmpty()) {
            throw GradleException("Inlined ECS queries failed (${failures.size} site(s)):\n" + failures.joinToString("\n") { "  $it" })
        }
        logger.lifecycle("Inlined ECS queries passed: no query is handed a function object")
    }
}
