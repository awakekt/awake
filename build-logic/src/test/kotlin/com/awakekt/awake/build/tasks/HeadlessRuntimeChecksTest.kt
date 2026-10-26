/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadlessRuntimeChecksTest {
    private val forbidden = listOf(":awake:backend", ":awake:engine:window", ":awake:project:transport")
    private val forbiddenNames = listOf(":awake:project:transport-")

    private fun failures(graph: Map<String, Set<String>>, vararg modules: String) =
        headlessRuntimeFailures(graph, modules.toList(), forbidden, forbiddenNames)

    @Test
    fun aRuntimeThatReachesNoDisplayModulePasses() {
        val graph = mapOf(
            RUNTIME to setOf(":awake:scene:authoring", ":awake:physics:api"),
            ":awake:scene:authoring" to setOf(":awake:engine:bootstrap"),
            ":awake:engine:bootstrap" to setOf(":awake:engine:platform"),
        )

        assertTrue(headlessRuntimeFailures(graph, listOf(RUNTIME), forbidden).isEmpty())
    }

    @Test
    fun aBackendTheRuntimeDependsOnFails() {
        val graph = mapOf(RUNTIME to setOf(":awake:backend:jolt"))

        assertEquals(listOf("$RUNTIME depends on :awake:backend:jolt"), headlessRuntimeFailures(graph, listOf(RUNTIME), forbidden))
    }

    @Test
    fun aWindowReachedThroughAnotherModuleFailsWithItsChain() {
        val graph = mapOf(
            RUNTIME to setOf(":awake:scene:authoring"),
            ":awake:scene:authoring" to setOf(":awake:engine:window"),
        )

        assertEquals(
            listOf("$RUNTIME reaches :awake:engine:window through $RUNTIME -> :awake:scene:authoring -> :awake:engine:window"),
            headlessRuntimeFailures(graph, listOf(RUNTIME), forbidden),
        )
    }

    @Test
    fun aTransportTheRuntimeDependsOnFails() {
        val graph = mapOf(RUNTIME to setOf(":awake:project:transport-ktor"), ":awake:project" to setOf(":awake:project:transport-browser"))

        assertEquals(
            listOf(
                "$RUNTIME depends on :awake:project:transport-ktor",
                ":awake:project depends on :awake:project:transport-browser",
            ),
            failures(graph, RUNTIME, ":awake:project"),
        )
    }

    @Test
    fun aTransportReachedThroughAnotherModuleFailsWithItsChain() {
        val graph = mapOf(
            ":awake:project" to setOf(":awake:core:io", ":awake:project:blueprint"),
            ":awake:project:blueprint" to setOf(":awake:project:transport-ktor"),
        )

        assertEquals(
            listOf(
                ":awake:project reaches :awake:project:transport-ktor through " +
                    ":awake:project -> :awake:project:blueprint -> :awake:project:transport-ktor",
            ),
            failures(graph, ":awake:project"),
        )
    }

    /** A transport added later is covered without touching the rule. */
    @Test
    fun anyOtherTransportModuleFailsToo() {
        val graph = mapOf(RUNTIME to setOf(":awake:project:transport-urlsession", ":awake:project:transport"))

        assertEquals(
            listOf("$RUNTIME depends on :awake:project:transport", "$RUNTIME depends on :awake:project:transport-urlsession"),
            failures(graph, RUNTIME).sorted(),
        )
    }

    /** The transports depend on `:awake:project`, never the other way round, and a module may sit beside them. */
    @Test
    fun aTransportMayDependOnTheProjectAndOtherProjectModulesPass() {
        val graph = mapOf(
            RUNTIME to setOf(":awake:project", ":awake:project:blueprint"),
            ":awake:project:transport-ktor" to setOf(":awake:project"),
        )

        assertTrue(failures(graph, RUNTIME).isEmpty())
    }

    /** A prefix is a module path, not a string prefix: `:awake:backends-docs` is not under `:awake:backend`. */
    @Test
    fun aPrefixMatchesWholePathSegmentsOnly() {
        val graph = mapOf(RUNTIME to setOf(":awake:backends-docs", ":awake:engine:windowing-notes", ":awake:project:transports-notes"))

        assertTrue(headlessRuntimeFailures(graph, listOf(RUNTIME), forbidden).isEmpty())
    }

    @Test
    fun aModuleThatIsNoLongerInTheBuildFails() {
        assertEquals(listOf("$RUNTIME is not a module of this build"), headlessRuntimeFailures(emptyMap(), listOf(RUNTIME), forbidden))
    }

    @Test
    fun aTestOnlyBackendIsNotAMainDependency() {
        val configurations = mapOf("commonMainApi" to listOf(":awake:project"), "commonTestImplementation" to listOf(":awake:backend:jolt"))
        val graph = mapOf(RUNTIME to mainProjectDependencies(configurations))

        assertTrue(headlessRuntimeFailures(graph, listOf(RUNTIME), forbidden).isEmpty())
    }

    private companion object {
        const val RUNTIME = ":awake:project:runtime"
    }
}
