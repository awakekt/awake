/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object ObservedBox

private object ObservedLeaf

private object ObservedPopup

class RememberObserverTest {

    private val root = FakeNode()
    private val forgotten = mutableListOf<String>()

    private inner class Tracked(val name: String) : RememberObserver {
        override fun onForgotten() {
            forgotten += name
        }
    }

    private fun pass(content: context(Composer) () -> Unit) {
        reconcile(FakeApplier(root), root, content)
    }

    @Test
    fun aPassThatKeepsEveryValueForgetsNothing() {
        val content: context(Composer)
        () -> Unit = {
            remember<Tracked> { Tracked("root") }
            node(ObservedBox, content = { remember<Tracked> { Tracked("box") } })
        }

        repeat(3) { pass(content) }

        assertTrue(forgotten.isEmpty())
    }

    @Test
    fun aChangedKeyForgetsThePreviousValue() {
        var key = 1
        val content: context(Composer)
        () -> Unit = { node(ObservedBox, content = { remember<Tracked>(key) { Tracked("v$key") } }) }

        pass(content)
        pass(content)
        key = 2
        pass(content)

        assertEquals(listOf("v1"), forgotten)
    }

    @Test
    fun aValueReplacedByAnotherCallsTypeIsForgotten() {
        var shifted = false
        val content: context(Composer)
        () -> Unit = {
            node(
                ObservedBox,
                content = {
                    if (shifted) remember { "a string now sits first" }
                    remember<Tracked> { Tracked("tracked") }
                },
            )
        }

        pass(content)
        shifted = true
        pass(content)

        assertEquals("tracked", forgotten.first())
    }

    @Test
    fun aCallThatStopsBeingMadeIsForgottenLastFirst() {
        var count = 3
        val content: context(Composer)
        () -> Unit = { node(ObservedBox, content = { repeat(count) { remember<Tracked> { Tracked("v$it") } } }) }

        pass(content)
        count = 1
        pass(content)

        assertEquals(listOf("v2", "v1"), forgotten)
    }

    @Test
    fun aKeyedBlockThatGoesForgetsWhatItRemembered() {
        var show = true
        val content: context(Composer)
        () -> Unit = {
            if (show) {
                key("panel") {
                    remember<Tracked> { Tracked("first") }
                    remember<Tracked> { Tracked("second") }
                }
            }
            remember<Tracked> { Tracked("after") }
        }

        pass(content)
        show = false
        pass(content)

        assertEquals(listOf("second", "first"), forgotten)
    }

    @Test
    fun aRemovedNodeForgetsItsDescendantsThenItself() {
        var show = true
        val content: context(Composer)
        () -> Unit = {
            if (show) {
                node(
                    ObservedBox,
                    content = {
                        remember<Tracked> { Tracked("box") }
                        node(ObservedLeaf, content = { remember<Tracked> { Tracked("leaf") } })
                        node(ObservedPopup, Slot.Layers, content = { remember<Tracked> { Tracked("popup") } })
                    },
                )
            }
        }

        pass(content)
        show = false
        pass(content)

        assertEquals(listOf("leaf", "popup", "box"), forgotten)
        assertTrue(root.children.isEmpty())
    }

    @Test
    fun aReusedNodeDeclaredWithoutContentForgetsItsDescendantsThenItsOwnSlots() {
        var withContent = true
        val content: context(Composer)
        () -> Unit = {
            if (withContent) {
                node(
                    ObservedBox,
                    content = {
                        remember<Tracked> { Tracked("box") }
                        node(ObservedLeaf, content = { remember<Tracked> { Tracked("leaf") } })
                        node(ObservedPopup, Slot.Layers, content = { remember<Tracked> { Tracked("popup") } })
                    },
                )
            } else {
                node(ObservedBox)
            }
        }

        pass(content)
        withContent = false
        pass(content)

        assertEquals(listOf("leaf", "popup", "box"), forgotten)
        assertTrue(root.children.single().rememberSlots.isEmpty())
    }

    @Test
    fun disposingACompositionForgetsEverything() {
        val composition = Composition(FakeApplier(root), root)
        composition.reconcile {
            remember<Tracked> { Tracked("root") }
            node(ObservedBox, content = { remember<Tracked> { Tracked("box") } })
        }

        composition.dispose()

        assertEquals(listOf("box", "root"), forgotten)
        assertTrue(root.children.isEmpty())
        assertTrue(root.rememberSlots.isEmpty())
    }

    @Test
    fun aRemovedNodeGoesEvenWhenItsValueThrows() {
        var show = true
        val content: context(Composer)
        () -> Unit = {
            if (show) {
                node(
                    ObservedBox,
                    content = {
                        remember<RememberObserver> { RememberObserver { error("release failed") } }
                    },
                )
            }
        }

        pass(content)
        show = false
        assertFailsWith<IllegalStateException> { pass(content) }

        assertTrue(root.children.isEmpty())
    }
}
