/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.state

import com.awakekt.awake.compose.runtime.Applier
import com.awakekt.awake.compose.runtime.Composition
import com.awakekt.awake.compose.runtime.RememberHolder
import com.awakekt.awake.compose.runtime.Slot
import com.awakekt.awake.core.state.store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A HUD's state: the player's health, and a name another composable shows. */
internal data class SelectedHud(val hp: Int, val name: String, val gold: Long = 0)

/**
 * `collectAsState { ... }` (#1512): a pass that read one part of a flow's state is invalid when that
 * part changes, and stays valid while another part, or nothing, does.
 */
class SelectedStateTest {
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
    fun onlyAChangeOfTheSelectedPartInvalidatesThePass() {
        val flow = MutableStateFlow(SelectedHud(hp = 100, name = "Harbor Town"))
        val seen = mutableListOf<Int>()
        composition.reconcile { seen += flow.collectAsState { it.hp }.value }

        flow.value = flow.value.copy(name = "Guild Hall")
        assertFalse(composition.isInvalid, "another part changed")
        flow.value = flow.value.copy() // A new, equal object.
        assertFalse(composition.isInvalid, "an equal state")

        flow.value = flow.value.copy(hp = 90)
        assertTrue(composition.isInvalid, "the selected part changed")
        composition.reconcile { seen += flow.collectAsState { it.hp }.value }
        assertEquals(listOf(100, 90), seen)
    }

    @Test
    fun anUnchangedFlowIsPolledWithoutSelectingAgain() {
        val flow = MutableStateFlow(SelectedHud(hp = 100, name = "Harbor Town"))
        var selections = 0
        val state = run {
            lateinit var read: com.awakekt.awake.compose.runtime.State<Int>
            composition.reconcile { read = flow.collectAsState { selections++; it.hp }.also { it.value } }
            read
        }
        val first = state.value
        repeat(POLLS) { assertFalse(composition.isInvalid) }
        assertSame(first, state.value, "the same instance comes back")
        assertEquals(1, selections, "selected once, for the flow's one object")
    }

    @Test
    fun aStoresSelectedPartInvalidatesOnlyWhenItChanges() {
        val hud = store(SelectedHud(hp = 100, name = "Harbor Town")).also { stores += it }
        composition.reconcile { hud.collectAsState { it.gold }.value }
        hud.update { it.copy(hp = 50) }
        assertFalse(composition.isInvalid)
        hud.update { it.copy(gold = 16_777_217L) }
        assertTrue(composition.isInvalid)
    }

    private companion object {
        const val POLLS = 50
    }
}
