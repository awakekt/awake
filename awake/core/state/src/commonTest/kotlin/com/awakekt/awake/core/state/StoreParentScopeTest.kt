/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A store run under a parent scope, the way an app runs its stores under its app coroutine scope:
 * the parent stands in for that scope, a [SupervisorJob] with a [CoroutineExceptionHandler].
 */
class StoreParentScopeTest {

    private val failures = mutableListOf<Throwable>()
    private val parent = CoroutineScope(
        SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, failure -> failures += failure },
    )

    @Test
    fun uncaughtStoreFailureReachesTheParentHandler() {
        val store = store(0, scope = parent.childStoreScope())
        val boom = IllegalStateException("store work failed")

        store.coroutineScope.launch { throw boom }

        assertEquals(listOf<Throwable>(boom), failures)
        assertTrue(store.coroutineScope.isActive, "a failed coroutine does not cancel its store")
        assertTrue(parent.isActive, "a failed store coroutine does not cancel the parent")
    }

    @Test
    fun cancellingTheParentCancelsStoreCoroutines() {
        val hud = reducerStore<Int, Int, Nothing>(0, scope = parent.childStoreScope()) { s, i -> s + i to null }
        val work = hud.coroutineScope.launch { awaitCancellation() }

        parent.cancel()

        assertTrue(work.isCancelled)
        assertFalse(hud.coroutineScope.isActive)
        assertTrue(failures.isEmpty(), "a cancellation is not a failure")
    }

    @Test
    fun closingOneStoreLeavesSiblingsAndParentRunning() {
        val closed = store("a", scope = parent.childStoreScope())
        val sibling = MutableEffectStore<String, Nothing>("b", parent.childStoreScope())
        val closedWork = closed.coroutineScope.launch { awaitCancellation() }
        val siblingWork = sibling.coroutineScope.launch { awaitCancellation() }

        closed.close()

        assertTrue(closedWork.isCancelled)
        assertTrue(siblingWork.isActive)
        assertTrue(sibling.coroutineScope.isActive)
        assertTrue(parent.isActive)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun defaultStoreScopeWithAParentIsItsChild() {
        val scope = DefaultStoreScope(parent)
        val work = scope.coroutineScope.launch { awaitCancellation() }

        assertSame(parent.coroutineContext.job, scope.coroutineScope.coroutineContext.job.parent)
        scope.close()
        assertTrue(work.isCancelled)
        assertTrue(parent.isActive)
    }

    @Test
    fun storeUsesTheParentDispatcherUnlessGivenOne() {
        val inherited = parent.childStoreScope()
        val explicit = parent.childStoreScope(Dispatchers.Default)
        val noDispatcher = CoroutineScope(Job()).childStoreScope()

        assertSame(Dispatchers.Unconfined, inherited.coroutineContext[ContinuationInterceptor])
        assertSame(Dispatchers.Default, explicit.coroutineContext[ContinuationInterceptor])
        assertSame(Dispatchers.Default, noDispatcher.coroutineContext[ContinuationInterceptor])
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun storesWithoutAParentKeepStandaloneScopes() {
        val scopes = listOf(
            store(0).coroutineScope,
            reducerStore<Int, Int, Nothing>(0) { s, i -> s + i to null }.coroutineScope,
            MutableEffectStore<Int, Nothing>(0).coroutineScope,
            DefaultStoreScope().coroutineScope,
        )

        scopes.forEach { scope ->
            assertNull(scope.coroutineContext.job.parent, "no parent unless one is given")
            assertSame(Dispatchers.Default, scope.coroutineContext[ContinuationInterceptor])
            scope.cancel()
        }
    }
}
