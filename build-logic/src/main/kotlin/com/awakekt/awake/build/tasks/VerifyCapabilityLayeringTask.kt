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
import org.gradle.api.provider.MapProperty
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
 * so a capability module's main code does not depend on an `awake:scene` module. It fails a module that
 * does, in any of three ways:
 *
 * - **directly**, a project dependency in a main configuration, wherever it was declared: the build
 *   file, a convention plugin, or an `afterEvaluate` block, since [projectDependencies] is read from the
 *   Gradle model rather than from the build file's text;
 * - **transitively**, through another module that does, since it cannot be used without the scene
 *   layer either;
 * - **in its sources**, a main source file that imports or fully qualifies a type in an `awake.scene`
 *   package, which catches a scene type arriving some other way, such as a published coordinate.
 *
 * The scene modules themselves, the composition modules that load and run whole projects, and
 * benchmark harnesses are [exemptModulePrefixes]. Modules that broke the rule before it existed are the
 * [debt] ledger: it may only shrink, and this task fails on a debt entry that has been paid off so it
 * gets deleted. See `awake/scene/README.md` for the rule and why.
 */
@DisableCachingByDefault(because = "Verification task with no outputs")
abstract class VerifyCapabilityLayeringTask : DefaultTask() {
    /** The repository root, which source paths are taken relative to. */
    @get:Input
    abstract val rootPath: Property<String>

    /** Module paths (`:awake:scene`) and the module paths under them that may depend on scene modules. */
    @get:Input
    abstract val exemptModulePrefixes: ListProperty<String>

    /** Modules that depend on a scene module in main code and have not been fixed yet. Shrink, never grow. */
    @get:Input
    abstract val debt: SetProperty<String>

    /**
     * Every `:awake:` module's project dependencies as Gradle resolved its build: module path to
     * configuration name to the project paths declared in that configuration. Which configurations are
     * main code is decided here, by [isMainDependencyConfiguration].
     */
    @get:Input
    abstract val projectDependencies: MapProperty<String, Map<String, List<String>>>

    /** The Kotlin files in each module's main source sets, its `src/<sourceSet>Main` directories. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainSources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val root = File(rootPath.get())
        val exempt = exemptModulePrefixes.get()
        fun isExempt(module: String) = exempt.any { module == it || module.startsWith("$it:") }

        val graph = projectDependencies.get().mapValues { (_, configurations) -> mainProjectDependencies(configurations) }
        val couplings = sortedMapOf<String, MutableList<String>>()
        graph.keys.filterNot(::isExempt).forEach { module ->
            val reasons = couplings.getOrPut(module) { mutableListOf() }
            sceneModulesReached(graph, module).forEach { (scene, chain) ->
                reasons += if (chain.size == 2) "depends on $scene" else "reaches $scene through ${chain.joinToString(" -> ")}"
            }
        }
        mainSources.files
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                val module = ":" + relative.substringBefore("/src/").replace('/', ':')
                if (isExempt(module)) return@forEach
                sceneReferenceLines(file.readText()).forEach { line ->
                    couplings.getOrPut(module) { mutableListOf() } += "$relative:$line names a scene type"
                }
            }

        val failures = capabilityLayeringFailures(couplings, debt.get())
        if (failures.isNotEmpty()) {
            throw GradleException(
                "Capability layering failed (${failures.size} problem(s)):\n" + failures.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("Capability layering passed: ${couplings.size} modules checked, ${debt.get().size} on the debt ledger")
    }
}
