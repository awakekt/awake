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
    private val forbidden = listOf(":awake:backend", ":awake:engine:window")

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

    /** A prefix is a module path, not a string prefix: `:awake:backends-docs` is not under `:awake:backend`. */
    @Test
    fun aPrefixMatchesWholePathSegmentsOnly() {
        val graph = mapOf(RUNTIME to setOf(":awake:backends-docs", ":awake:engine:windowing-notes"))

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
