/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CoroutineScopeChecksTest {
    // --- finding constructions

    @Test
    fun aScopeFactoryCallIsASite() {
        val source = """
            class Loader {
                private val loads = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                private val ui = MainScope()
                private val qualified = kotlinx.coroutines.CoroutineScope (Job())
            }
        """.trimIndent()

        assertEquals(listOf(2, 3, 4), scopeConstructionLines(source))
    }

    @Test
    fun twoConstructionsOnOneLineCountTwice() {
        assertEquals(listOf(1, 1), scopeConstructionLines("val a = CoroutineScope(Job()); val b = CoroutineScope(Job())"))
    }

    @Test
    fun typesSupertypesReceiversAndDeclarationsAreNotSites() {
        val source = """
            class Owner(scope: CoroutineScope) : CoroutineScope by scope
            fun CoroutineScope.childScope(): CoroutineScope = this
            fun CoroutineScope(context: CoroutineContext): CoroutineScope = TODO()
            val other = mine.CoroutineScope(Job())
            val custom = MyCoroutineScope(Job())
        """.trimIndent()

        assertEquals(emptyList(), scopeConstructionLines(source))
    }

    @Test
    fun commentsAndStringsAreNotSites() {
        val source = """
            // CoroutineScope(SupervisorJob())
            /** Not `MainScope()`: it may resolve to a Swing dispatcher. */
            val message = "CoroutineScope(Job())"
        """.trimIndent()

        assertEquals(emptyList(), scopeConstructionLines(source))
    }

    @Test
    fun globalScopeIsFoundInCodeAndImportsButNotInComments() {
        val source = """
            import kotlinx.coroutines.GlobalScope
            // GlobalScope.launch { }
            fun go() = GlobalScope.launch { }
        """.trimIndent()

        assertEquals(listOf(1, 3), globalScopeLines(source))
    }

    // --- the baseline

    @Test
    fun theBaselineReadsPathsAndCountsAndSkipsComments() {
        val text = """
            # header
            awake/a/src/commonMain/kotlin/A.kt 2

            awake/b/src/desktopMain/kotlin/B.kt   1
        """.trimIndent()

        assertEquals(mapOf("awake/a/src/commonMain/kotlin/A.kt" to 2, "awake/b/src/desktopMain/kotlin/B.kt" to 1), parseScopeBaseline(text))
    }

    @Test
    fun aBaselineLineWithoutAPositiveCountIsRejected() {
        assertFailsWith<IllegalArgumentException> { parseScopeBaseline("awake/a/A.kt") }
        assertFailsWith<IllegalArgumentException> { parseScopeBaseline("awake/a/A.kt 0") }
    }

    // --- failures

    @Test
    fun sitesTheBaselineAllowsPass() {
        assertEquals(emptyList(), coroutineScopeFailures(mapOf(A to listOf(3, 9)), mapOf(A to 2), emptyMap()))
    }

    @Test
    fun aNewSiteFails() {
        val failures = coroutineScopeFailures(mapOf(A to listOf(3, 9), B to listOf(4)), mapOf(A to 2), emptyMap())

        assertEquals(1, failures.size)
        assertTrue(failures.single().startsWith("$B:4 constructs 1 coroutine scope(s) where the baseline allows 0"), failures.single())
    }

    @Test
    fun aSiteBeyondItsAllowanceFails() {
        val failures = coroutineScopeFailures(mapOf(A to listOf(3, 9, 12)), mapOf(A to 2), emptyMap())

        assertTrue(failures.single().startsWith("$A:3,9,12 constructs 3"), failures.single())
    }

    @Test
    fun aMigratedSiteMustLeaveTheBaseline() {
        val lowered = coroutineScopeFailures(mapOf(A to listOf(3)), mapOf(A to 2), emptyMap())
        val gone = coroutineScopeFailures(emptyMap(), mapOf(A to 1), emptyMap())

        assertTrue(lowered.single().contains("allows 2 coroutine scope(s) but 1 remain"), lowered.single())
        assertTrue(gone.single().contains("allows 1 coroutine scope(s) but 0 remain"), gone.single())
    }

    @Test
    fun globalScopeAlwaysFails() {
        val failures = coroutineScopeFailures(emptyMap(), emptyMap(), mapOf(A to listOf(7)))

        assertEquals(listOf("$A:7 uses GlobalScope. Launch in a child of an approved owner instead."), failures)
    }

    @Test
    fun aMissingOwnerFails() {
        val failures = coroutineScopeFailures(emptyMap(), emptyMap(), emptyMap(), missingOwners = listOf(B))

        assertEquals(listOf("$B is listed as an approved coroutine scope owner but does not exist."), failures)
    }

    private companion object {
        const val A = "awake/a/src/commonMain/kotlin/A.kt"
        const val B = "awake/b/src/commonMain/kotlin/B.kt"
    }
}
