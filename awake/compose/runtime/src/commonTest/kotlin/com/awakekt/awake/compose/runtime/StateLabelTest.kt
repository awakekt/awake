/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A labelled state names itself wherever it is printed (#1512): `ComposeHost.explainPlacement`
 * reports the state placement wrote, which a bare `MutableState(value=272)` did not trace to an owner.
 */
class StateLabelTest {
    @Test
    fun aLabelledStateNamesItself() {
        val state = mutableStateOf(272, "FollowerPlans.composeRevision")
        assertEquals("MutableState(FollowerPlans.composeRevision, value=272)", state.toString())
        state.value = 274
        assertEquals("MutableState(FollowerPlans.composeRevision, value=274)", state.toString())
    }

    @Test
    fun anUnlabelledStatePrintsAsBefore() {
        assertEquals("MutableState(value=1)", mutableStateOf(1).toString())
    }

    @Test
    fun aLabelledStateIsReadTrackedLikeAnyOther() {
        val root = FakeNode()
        val composition = Composition(FakeApplier(root), root)
        val state = mutableStateOf(0, "counter")
        composition.reconcile { state.value }
        state.value = 1
        assertTrue(composition.isInvalid, "a write of a read labelled state invalidates the composition")
    }
}
