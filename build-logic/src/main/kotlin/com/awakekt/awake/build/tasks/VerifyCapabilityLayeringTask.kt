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
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Keeps capabilities independent of the scene layer.
 *
 * Awake is a library first. `awake:scene:*` binds capabilities into the ECS scene graph and the scene
 * document; a capability, an algorithm or behaviour with an API of its own, must be usable without it,
 * so a capability module does not depend on an `awake:scene` module in its main source sets.
 *
 * The scene modules themselves, the composition modules that load and run whole projects, and
 * benchmark harnesses are [exemptModulePrefixes]. Modules that broke the rule before it existed are the
 * [debt] ledger: it may only shrink, and this task fails on a debt entry that has been paid off so it
 * gets deleted. See `awake/scene/README.md` for the rule and why.
 */
@DisableCachingByDefault(because = "Verification task with no outputs")
abstract class VerifyCapabilityLayeringTask : DefaultTask() {
    /** The repository root, which module paths are taken relative to. */
    @get:Input
    abstract val rootPath: Property<String>

    /** Module paths (`:awake:ai:behavior`) and the module paths under them that may depend on scene modules. */
    @get:Input
    abstract val exemptModulePrefixes: ListProperty<String>

    /** Modules that depend on a scene module in main code and have not been fixed yet. Shrink, never grow. */
    @get:Input
    abstract val debt: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val root = File(rootPath.get())
        val exempt = exemptModulePrefixes.get()
        val dependencies = buildFiles.files
            .filter { it.isFile && it.name == "build.gradle.kts" }
            .associate { file -> modulePath(root, file) to sceneDependenciesInMainCode(file.readText()) }
            .filterKeys { module -> exempt.none { module == it || module.startsWith("$it:") } }
        val failures = capabilityLayeringFailures(dependencies, debt.get())
        if (failures.isNotEmpty()) {
            throw GradleException(
                "Capability layering failed (${failures.size} problem(s)):\n" + failures.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("Capability layering passed: ${dependencies.size} modules checked, ${debt.get().size} on the debt ledger")
    }

    private fun modulePath(root: File, buildFile: File): String =
        ":" + buildFile.parentFile.relativeTo(root).invariantSeparatorsPath.replace('/', ':')
}
