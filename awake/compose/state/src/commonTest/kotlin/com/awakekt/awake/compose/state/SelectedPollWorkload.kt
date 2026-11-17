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
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [size] parts of one HUD flow, each read through `collectAsState { ... }` in one composition, and
 * the check a skipping host makes each frame: whether the composition is invalid (#1512).
 */
internal class SelectedPollWorkload(size: Int) {
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

    val flow = MutableStateFlow(SelectedHud(hp = 100, name = "Harbor Town", gold = 1_234_567L))
    private val composition = Composition(NoNodes, Root())

    init {
        composition.reconcile {
            repeat(size) { part ->
                // Boxed parts, a Float and a Long, as a HUD reads them, so a poll that boxed would show.
                if (part % 2 == 0) flow.collectAsState { it.hp * 0.5f }.value else flow.collectAsState { it.gold }.value
            }
        }
    }

    /** One frame's check. */
    fun poll(): Boolean = composition.isInvalid
}
