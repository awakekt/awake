/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

/**
 * Compiled definitions by asset path, so the agents that name one definition share one compile,
 * and a reload is seen by everything that uses the path.
 *
 * It reads text it is given and does no I/O: a host or a played project reads the files and calls
 * [loadTree] or [loadStateMachine]. Each load of a path bumps its [version], which is how a system
 * that built agents from it knows to rebuild them.
 *
 * @property registry The kinds definitions may use.
 * @property json How definitions are read, including any format migrations.
 */
class AiDefinitionLibrary(
    val registry: AiRegistry = AiRegistry(),
    val json: AiDefinitionJson = AiDefinitionJson.Default,
) {
    private val trees = HashMap<String, CompiledBehaviorTree>()
    private val machines = HashMap<String, CompiledStateMachine>()
    private val versions = HashMap<String, Int>()

    /**
     * Reads and compiles the behaviour tree [text] as [path], replacing what was there.
     *
     * @throws AiDefinitionException naming [path] in each problem's location.
     */
    fun loadTree(path: String, text: String): CompiledBehaviorTree {
        val compiled = located(path) { registry.compileTree(json.decodeTree(text)) }
        trees[path] = compiled
        machines.remove(path)
        bump(path)
        return compiled
    }

    /**
     * Reads and compiles the state machine [text] as [path], replacing what was there.
     *
     * @throws AiDefinitionException naming [path] in each problem's location.
     */
    fun loadStateMachine(path: String, text: String): CompiledStateMachine {
        val compiled = located(path) { registry.compileStateMachine(json.decodeStateMachine(text)) }
        machines[path] = compiled
        trees.remove(path)
        bump(path)
        return compiled
    }

    /** The behaviour tree loaded as [path], or `null`. */
    fun tree(path: String): CompiledBehaviorTree? = trees[path]

    /** The state machine loaded as [path], or `null`. */
    fun stateMachine(path: String): CompiledStateMachine? = machines[path]

    /** How many times [path] has been loaded; 0 when it never was. */
    fun version(path: String): Int = versions[path] ?: 0

    /** Drops [path], whose agents keep what they were built with. */
    fun forget(path: String) {
        trees.remove(path)
        machines.remove(path)
        bump(path)
    }

    private fun bump(path: String) {
        versions[path] = version(path) + 1
    }

    private inline fun <T> located(path: String, block: () -> T): T =
        try {
            block()
        } catch (
            // The problems are carried over whole; only their locations gain the path.
            @Suppress("SwallowedException") e: AiDefinitionException,
        ) {
            throw AiDefinitionException(
                e.problems.map { it.copy(location = if (it.location.isEmpty()) path else "$path: ${it.location}") },
            )
        }
}
