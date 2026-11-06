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
import org.gradle.api.provider.ListProperty
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
 * Keeps every long-lived coroutine scope under an owner that cancels it.
 *
 * Awake's scopes form one tree: the app's `AppCoroutineScope`, a scene's `SceneSession.coroutineScope`
 * under it, `core:state`'s store scopes, and the `net:server-ktor` host's. Those [approvedOwners] are the
 * only main sources that may construct a scope with `CoroutineScope(...)` or `MainScope()`; anything
 * else runs its work in a child of one, so disposing the scene or the app stops it and its failures
 * reach the app's error handler. `GlobalScope` is never allowed.
 *
 * The constructions that predate the rule are in [baseline], a file of `<path> <count>` lines that can
 * only shrink: a file with more sites than its line allows fails, and so does a line that allows more
 * than its file still has, so a migrated site takes its allowance with it. Each remaining line is a
 * checklist item on the tracking issue named in the baseline's header.
 */
@DisableCachingByDefault(because = "Verification task with no outputs")
abstract class VerifyCoroutineScopesTask : DefaultTask() {
    /** The repository root, which source and owner paths are taken relative to. */
    @get:Input
    abstract val rootPath: Property<String>

    /** Repository-relative paths of the source files allowed to construct a scope. */
    @get:Input
    abstract val approvedOwners: ListProperty<String>

    /** The checked-in `<path> <count>` list of constructions waiting to be migrated. */
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
        val owners = approvedOwners.get().toSet()
        val sites = sortedMapOf<String, List<Int>>()
        val globalScope = sortedMapOf<String, List<Int>>()
        mainSources.files
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                val source = file.readText()
                globalScopeLines(source).takeIf { it.isNotEmpty() }?.let { globalScope[relative] = it }
                if (relative in owners) return@forEach
                scopeConstructionLines(source).takeIf { it.isNotEmpty() }?.let { sites[relative] = it }
            }
        val allowed = parseScopeBaseline(baseline.get().asFile.readText())
        val missingOwners = owners.filterNot { root.resolve(it).isFile }

        val failures = coroutineScopeFailures(sites, allowed, globalScope, missingOwners)
        if (failures.isNotEmpty()) {
            throw GradleException(
                "Coroutine scope ownership failed (${failures.size} problem(s)):\n" + failures.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("Coroutine scope ownership passed: ${sites.values.sumOf { it.size }} baselined site(s) in ${sites.size} file(s)")
    }
}
