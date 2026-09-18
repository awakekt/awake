/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private object StateParent

class MutableStateTest {

    @Test
    fun mutableStateHoldsValueDirectly() {
        val state = mutableStateOf(42)
        assertEquals(42, state.value)

        state.value = 100
        assertEquals(100, state.value)
    }

    @Test
    fun mutableStateSupportsPropertyDelegation() {
        var count by mutableStateOf(0)
        assertEquals(0, count)

        count = 5
        assertEquals(5, count)

        count += 10
        assertEquals(15, count)
    }

    @Test
    fun stateSupportsReadPropertyDelegation() {
        val state: State<String> = mutableStateOf("hello")
        val message by state
        assertEquals("hello", message)
    }

    @Test
    fun mutableStateSupportsDestructuring() {
        val (value, setValue) = mutableStateOf("initial")
        assertEquals("initial", value)

        setValue("updated")
        // The original state reference has the updated value
        // but the destructured `value` val was captured by value
        val state = mutableStateOf(10)
        val (_, setNum) = state
        setNum(25)
        assertEquals(25, state.value)
    }

    @Test
    fun rememberedMutableStateSurvivesAcrossPasses() {
        val root = FakeNode()
        val values = mutableListOf<Int>()
        var externalTrigger = false

        val content: context(Composer)
        () -> Unit = {
            node(StateParent) {
                var stateCount by remember { mutableStateOf(0) }
                if (externalTrigger) {
                    stateCount += 1
                }
                values += stateCount
            }
        }

        pass(root, content)
        assertEquals(listOf(0), values)

        // Second pass: value should be retained
        pass(root, content)
        assertEquals(listOf(0, 0), values)

        // Mutate in third pass
        externalTrigger = true
        pass(root, content)
        assertEquals(listOf(0, 0, 1), values)

        // Fourth pass without mutation: retained at 1
        externalTrigger = false
        pass(root, content)
        assertEquals(listOf(0, 0, 1, 1), values)
    }

    @Test
    fun toStringFormatsCleanly() {
        val state = mutableStateOf("abc")
        assertEquals("MutableState(value=abc)", state.toString())
    }

    private fun pass(root: FakeNode, content: context(Composer) () -> Unit) {
        reconcile(FakeApplier(root), root, content)
    }
}
