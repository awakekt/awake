/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Keeps a played project runnable with no display.
 *
 * A game server or a CI runner loads a project through the project runtime with no renderer, so the
 * runtime must not bring a GPU backend or a window with it: the host picks the backends a played project
 * draws and simulates with, and passes them in. Nor may it bring an HTTP stack: a content transport
 * (`:awake:project:transport-ktor`, `:awake:project:transport-browser`) wraps the host's own client, and
 * the host passes it to `TokenAuthorizedFetch`. [modules]' main code therefore reaches no module under
 * [forbiddenModulePrefixes], or whose path starts with one of [forbiddenModuleNamePrefixes], directly or
 * through another module, read from the Gradle model as [VerifyCapabilityLayeringTask] reads it. Tests
 * may: a test that draws, steps physics or fetches over HTTP brings its own.
 */
@DisableCachingByDefault(because = "Verification task with no outputs")
abstract class VerifyHeadlessRuntimeTask : DefaultTask() {
    /** The modules a renderer-free host loads, such as `:awake:project:runtime`. */
    @get:Input
    abstract val modules: ListProperty<String>

    /** Module paths (`:awake:backend`) that those modules, and everything under the paths, must not reach. */
    @get:Input
    abstract val forbiddenModulePrefixes: ListProperty<String>

    /**
     * Module path prefixes (`:awake:project:transport-`) that, unlike [forbiddenModulePrefixes], match
     * the start of a module's last path segment too, so one entry forbids a whole family of sibling
     * modules named alike, including ones added later.
     */
    @get:Input
    abstract val forbiddenModuleNamePrefixes: ListProperty<String>

    /** Every `:awake:` module's project dependencies: module path to configuration name to project paths. */
    @get:Input
    abstract val projectDependencies: MapProperty<String, Map<String, List<String>>>

    @TaskAction
    fun verify() {
        val graph = projectDependencies.get().mapValues { (_, configurations) -> mainProjectDependencies(configurations) }
        val failures = headlessRuntimeFailures(graph, modules.get(), forbiddenModulePrefixes.get(), forbiddenModuleNamePrefixes.get())
        if (failures.isNotEmpty()) {
            throw GradleException(
                "Headless runtime check failed (${failures.size} problem(s)); the host picks its GPU backends, window and " +
                    "content transport (HTTP client) and passes them in, so none may be on the runtime's dependency path:\n" +
                    failures.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("Headless runtime passed: ${modules.get().joinToString()} reach no display or transport module")
    }
}

/**
 * Each of [modules] that reaches a module under [forbidden], or whose path starts with one of
 * [forbiddenNames], through [graph]'s main dependencies, as
 * "`module` reaches `target` through `a -> b -> target`". A module missing from [graph] is a failure too,
 * so a renamed module cannot pass by no longer being checked.
 */
internal fun headlessRuntimeFailures(
    graph: Map<String, Set<String>>,
    modules: List<String>,
    forbidden: List<String>,
    forbiddenNames: List<String> = emptyList(),
): List<String> {
    fun isForbidden(module: String) =
        forbidden.any { module == it || module.startsWith("$it:") } || forbiddenNames.any { module.startsWith(it) }
    return modules.flatMap { module ->
        if (module !in graph) return@flatMap listOf("$module is not a module of this build")
        modulesReached(graph, module, ::isForbidden).map { (target, chain) ->
            if (chain.size == 2) "$module depends on $target" else "$module reaches $target through ${chain.joinToString(" -> ")}"
        }
    }
}
