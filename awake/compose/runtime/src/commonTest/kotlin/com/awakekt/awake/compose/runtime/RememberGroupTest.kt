/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

private object Holder

private object Leaf

private object First

private object Second

/**
 * A call that comes or goes partway through a node must not hand the calls after it another call's
 * remembered value: a keyed block or a component's group keeps its state, the calls after it keep
 * theirs, and a value of another type is recomputed rather than cast.
 */
class RememberGroupTest {

    private val root = FakeNode()

    private fun pass(content: context(Composer) () -> Unit) {
        reconcile(FakeApplier(root), root, content)
    }

    @Test
    fun aKeyedBranchThatComesAndGoesLeavesTheCallAfterItItsOwnValue() {
        var show = true
        val after = mutableListOf<Any>()
        val content: context(Composer)
        () -> Unit = {
            node(Holder) {
                if (show) key("branch") { remember<Any> { Any() } }
                after += remember<Any> { Any() }
            }
        }

        pass(content)
        show = false
        pass(content)
        show = true
        pass(content)

        assertSame(after[0], after[1], "the branch going handed its value to the call after it")
        assertSame(after[0], after[2], "the branch coming back took the slot of the call after it")
    }

    @Test
    fun aValueOfAnotherTypeIsRecomputedRatherThanCast() {
        var show = true
        val counts = mutableListOf<Int>()
        val content: context(Composer)
        () -> Unit = {
            node(Holder) {
                if (show) remember { "a branch's text" }
                counts += remember { 7 }
            }
        }

        pass(content)
        show = false
        pass(content)

        assertEquals(listOf(7, 7), counts)
    }

    @Test
    fun keyedItemsKeepTheirStateWhenTheListReorders() {
        var ids = listOf("a", "b", "c")
        val seen = mutableListOf<Map<String, Any>>()
        val content: context(Composer)
        () -> Unit = {
            node(Holder) {
                val pass = mutableMapOf<String, Any>()
                for (id in ids) key(id) { pass[id] = remember<Any> { Any() } }
                seen += pass
            }
        }

        pass(content)
        ids = listOf("c", "a", "b")
        pass(content)

        for (id in listOf("a", "b", "c")) assertSame(seen[0][id], seen[1][id], "$id lost its state when the list reordered")
    }

    @Test
    fun theCallAfterAKeyedListKeepsItsValueAsTheListShrinksAndGrows() {
        var ids = listOf("a", "b", "c")
        val after = mutableListOf<Any>()
        val content: context(Composer)
        () -> Unit = {
            node(Holder) {
                for (id in ids) key(id) { remember<Any> { Any() } }
                after += remember<Any> { Any() }
            }
        }

        pass(content)
        ids = listOf("a")
        pass(content)
        ids = listOf("a", "b", "c", "d")
        pass(content)

        assertSame(after[0], after[1], "shrinking the list moved the call after it")
        assertSame(after[0], after[2], "growing the list moved the call after it")
    }

    @Test
    fun aKeyedBlockKeepsItsStateWhenAnUnkeyedCallBeforeItComesAndGoes() {
        var show = true
        val keyed = mutableListOf<Any>()
        val content: context(Composer)
        () -> Unit = {
            node(Holder) {
                if (show) remember { "unkeyed" }
                key("k") { keyed += remember<Any> { Any() } }
            }
        }

        pass(content)
        show = false
        pass(content)
        show = true
        pass(content)

        assertSame(keyed[0], keyed[1])
        assertSame(keyed[0], keyed[2])
    }

    @Test
    fun aComponentsGroupKeepsItsStateWhenAnotherComponentBeforeItComesAndGoes() {
        var showFirst = true
        val second = mutableListOf<Any>()
        val content: context(Composer)
        () -> Unit = {
            node(Holder) {
                if (showFirst) rememberGroup(First) { remember<Any> { Any() } }
                rememberGroup(Second) { second += remember<Any> { Any() } }
            }
        }

        pass(content)
        showFirst = false
        pass(content)
        showFirst = true
        pass(content)

        assertSame(second[0], second[1])
        assertSame(second[0], second[2])
    }

    @Test
    fun aGroupTakesOneSlotOfItsNodeHoweverMuchItRemembers() {
        pass {
            node(Holder) {
                rememberGroup(First) {
                    remember { 1 }
                    remember { 2 }
                }
                remember { 3 }
            }
        }

        assertEquals(2, root.children[0].rememberSlots.size)
    }

    @Test
    fun aComponentsGroupLeavesTheNodeKeyItsCallerGave() {
        pass { key("item") { rememberGroup(First) { node(Leaf) } } }

        assertEquals("item", root.children[0].key)
    }

    @Test
    fun aBranchThatLeavesIsComputedAfreshWhenItReturns() {
        var show = true
        val branch = mutableListOf<Any>()
        val content: context(Composer)
        () -> Unit = {
            node(Holder) { if (show) key("branch") { branch += remember<Any> { Any() } } }
        }

        pass(content)
        show = false
        pass(content)
        show = true
        pass(content)

        assertNotSame(branch[0], branch[1], "a branch's state outlived the branch")
    }
}
