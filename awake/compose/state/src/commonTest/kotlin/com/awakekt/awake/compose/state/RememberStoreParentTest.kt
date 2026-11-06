/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.state

import com.awakekt.awake.compose.runtime.Applier
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.Composition
import com.awakekt.awake.compose.runtime.RememberHolder
import com.awakekt.awake.compose.runtime.Slot
import com.awakekt.awake.core.state.Store
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Remembered stores run under a parent scope and are closed when the composition forgets them. */
class RememberStoreParentTest {

    private class Root : RememberHolder {
        override val rememberSlots: MutableList<Any?> = mutableListOf()
    }

    /** A tree with no nodes: these stores are remembered at the root. */
    private object NoNodes : Applier {
        override fun childCount(slot: Slot) = 0
        override fun typeAt(slot: Slot, index: Int): Any? = null
        override fun keyAt(slot: Slot, index: Int): Any? = null
        override fun nodeAt(slot: Slot, index: Int): Any = Unit
        override fun createAt(slot: Slot, index: Int, type: Any, key: Any?): Any = Unit
        override fun moveTo(slot: Slot, from: Int, to: Int) = Unit
        override fun truncateFrom(slot: Slot, index: Int) = Unit
        override fun down(slot: Slot, index: Int) = Unit
        override fun up() = Unit
    }

    private val failures = mutableListOf<Throwable>()
    private val parent = CoroutineScope(
        SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, failure -> failures += failure },
    )
    private val composition = Composition(NoNodes, Root())

    private fun pass(content: context(Composer) () -> Unit) {
        composition.reconcile(content)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun theStoreRunsUnderTheParentAndKeepsItsInstance() {
        val seen = mutableListOf<Store<Int>>()
        repeat(2) { pass { seen += rememberStore(parent) { 0 } } }

        assertSame(seen[0], seen[1])
        assertSame(parent.coroutineContext.job, seen[0].coroutineScope.coroutineContext.job.parent)
        val boom = IllegalStateException("store work failed")
        seen[0].coroutineScope.launch { throw boom }
        assertEquals(listOf<Throwable>(boom), failures)
    }

    @Test
    fun aChangedKeyClosesThePreviousStoreOnly() {
        var key = 1
        val seen = mutableListOf<Store<Int>>()
        val content: context(Composer)
        () -> Unit = { seen += rememberStore(parent, key) { key } }

        pass(content)
        key = 2
        pass(content)

        assertNotSame(seen[0], seen[1])
        assertFalse(seen[0].coroutineScope.isActive, "the forgotten store is closed")
        assertTrue(seen[1].coroutineScope.isActive)
        assertTrue(parent.isActive, "closing a store leaves the parent running")
    }

    @Test
    fun aStoreWhoseCallStopsIsClosedAndItsWorkCancelled() {
        var show = true
        var hud: Store<String>? = null
        val content: context(Composer)
        () -> Unit = {
            if (show) {
                hud = rememberReducerStore<String, String, Nothing>(parent, initialState = { "" }) { _, i -> i to null }
            }
        }

        pass(content)
        val work = hud!!.coroutineScope.launch { awaitCancellation() }
        show = false
        pass(content)

        assertTrue(work.isCancelled)
        assertTrue(failures.isEmpty(), "a closed store is not a failure")
    }

    @Test
    fun disposingTheCompositionClosesEveryStoreButNotTheParent() {
        val stores = mutableListOf<Store<*>>()
        pass {
            stores += rememberStore(parent) { 0 }
            stores += rememberStore { "standalone" }
            stores += rememberReducerStore<Int, Int, Nothing>(initialState = { 0 }) { s, i -> s + i to null }
        }

        composition.dispose()

        stores.forEach { assertFalse(it.coroutineScope.isActive) }
        assertTrue(parent.isActive)
    }

    @Test
    fun cancellingTheParentCancelsRememberedStores() {
        var store: Store<Int>? = null
        pass { store = rememberStore(parent) { 0 } }

        parent.cancel()

        assertFalse(store!!.coroutineScope.isActive)
    }
}
