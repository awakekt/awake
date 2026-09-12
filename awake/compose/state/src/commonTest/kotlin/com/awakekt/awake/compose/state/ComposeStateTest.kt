/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.state

import com.awakekt.awake.compose.runtime.Applier
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.RememberHolder
import com.awakekt.awake.compose.runtime.Slot
import com.awakekt.awake.compose.runtime.reconcile
import com.awakekt.awake.core.state.store
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ComposeStateTest {

    private class TestNode : RememberHolder {
        private val _rememberSlots = mutableListOf<Any?>()
        override val rememberSlots: List<Any?> get() = _rememberSlots
    }

    private class TestApplier : Applier {
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

    data class FormState(val text: String = "initial")

    sealed interface FormIntent {
        data class SetText(val newText: String) : FormIntent
        data object Submit : FormIntent
    }

    sealed interface FormEffect {
        data class Submitted(val text: String) : FormEffect
    }

    private fun compose(root: TestNode = TestNode(), content: context(Composer) () -> Unit) {
        reconcile(TestApplier(), root, content)
    }

    @Test
    fun rememberStoreRetainsInstanceAcrossRenders() {
        val root = TestNode()
        var firstInstance: Any? = null
        var secondInstance: Any? = null

        compose(root) {
            firstInstance = rememberStore { FormState() }
        }
        compose(root) {
            secondInstance = rememberStore { FormState() }
        }

        assertSame(firstInstance, secondInstance)
    }

    @Test
    fun rememberReducerStoreDispatchesAndDrainsEffects() {
        compose {
            val store = rememberReducerStore<FormState, FormIntent, FormEffect>(
                initialState = { FormState() },
            ) { state, intent ->
                when (intent) {
                    is FormIntent.SetText -> state.copy(text = intent.newText) to null
                    is FormIntent.Submit -> state to FormEffect.Submitted(state.text)
                }
            }

            assertEquals("initial", store.value.text)
            store.dispatch(FormIntent.SetText("updated"))
            assertEquals("updated", store.value.text)

            store.dispatch(FormIntent.Submit)
            val drained = store.drainEffects()
            assertEquals(1, drained.size)
            assertEquals("updated", (drained[0] as FormEffect.Submitted).text)
        }
    }

    @Test
    fun provideStoreAndUseStoreResolvesCorrectly() {
        val rootStore = store(FormState("root"))

        var resolvedText = ""
        compose {
            ProvideStore(rootStore) {
                val resolvedStore = useStore<FormState>()
                resolvedText = resolvedStore.value.text
            }
        }

        assertEquals("root", resolvedText)
    }
}
