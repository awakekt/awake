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
import com.awakekt.awake.compose.runtime.State
import com.awakekt.awake.compose.runtime.getValue
import com.awakekt.awake.core.state.store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CollectAsStateTest {

    private class Root : RememberHolder {
        private val slots = mutableListOf<Any?>()
        override val rememberSlots: List<Any?> get() = slots
    }

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

    private val composition = Composition(NoNodes, Root())
    private val stores = mutableListOf<AutoCloseable>()

    @AfterTest
    fun closeStores() = stores.forEach { it.close() }

    @Test
    fun theStateReadsTheFlowsCurrentValue() {
        val flow = MutableStateFlow("first")
        val seen = mutableListOf<String>()
        composition.reconcile {
            val text by flow.collectAsState()
            seen += text
        }
        flow.value = "second"
        composition.reconcile {
            val text by flow.collectAsState()
            seen += text
        }

        assertEquals(listOf("first", "second"), seen)
    }

    @Test
    fun aNewValueInvalidatesThePassThatReadIt() {
        val flow = MutableStateFlow(1)
        composition.reconcile { flow.collectAsState().value }
        assertFalse(composition.isInvalid)

        flow.value = 2

        assertTrue(composition.isInvalid)
    }

    @Test
    fun aPassThatNeverReadTheValueIsNotInvalidated() {
        val flow = MutableStateFlow(1)
        composition.reconcile { flow.collectAsState() }

        flow.value = 2

        assertFalse(composition.isInvalid)
    }

    @Test
    fun theSameStateComesBackWhileTheFlowStaysTheSame() {
        val flow = MutableStateFlow(1)
        val states = mutableListOf<State<Int>>()
        repeat(2) { composition.reconcile { states += flow.collectAsState() } }

        assertSame(states[0], states[1])
    }

    @Test
    fun aStoreUpdateInvalidatesThePassThatReadItsState() {
        val counter = store(0).also { stores += it }
        val seen = mutableListOf<Int>()
        val content: context(Composer)
        () -> Unit = {
            seen += counter.collectAsState().value
        }
        composition.reconcile(content)

        counter.update { it + 1 }

        assertTrue(composition.isInvalid)
        composition.reconcile(content)
        assertFalse(composition.isInvalid)
        assertEquals(listOf(0, 1), seen)
    }
}
