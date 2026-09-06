/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private object Parent

private object Child

class RememberTest {

    @Test
    fun aValueSurvivesTheNextPass() {
        val root = FakeNode()
        val seen = mutableListOf<Int>()
        var computed = 0
        val content: context(Composer)
        () -> Unit = {
            node(Parent, content = { seen += remember { computed++ } })
        }

        pass(root, content)
        pass(root, content)
        pass(root, content)

        assertEquals(1, computed, "recomputed on a later pass")
        assertEquals(listOf(0, 0, 0), seen)
    }

    @Test
    fun theSameInstanceComesBack() {
        val root = FakeNode()
        val seen = mutableListOf<Any>()
        val content: context(Composer)
        () -> Unit = {
            node(Parent, content = { seen += remember<Any> { Any() } })
        }

        pass(root, content)
        pass(root, content)

        assertSame(seen[0], seen[1])
    }

    @Test
    fun slotsAreIndependentWithinOneNode() {
        val root = FakeNode()
        val seen = mutableListOf<List<String>>()
        val content: context(Composer)
        () -> Unit = {
            node(
                Parent,
                content = {
                    val first: String = remember { "first" }
                    val second: String = remember { "second" }
                    seen += listOf(first, second)
                },
            )
        }

        pass(root, content)
        pass(root, content)

        assertEquals(listOf(listOf("first", "second"), listOf("first", "second")), seen)
        assertEquals(2, root.children[0].rememberSlots.size)
    }

    @Test
    fun aChangedKeyRecomputes() {
        val root = FakeNode()
        var version = 1
        val built = mutableListOf<String>()
        val content: context(Composer)
        () -> Unit = {
            node(Parent, content = { built += remember(version) { "built $version" } })
        }

        pass(root, content)
        version = 2
        pass(root, content)

        assertEquals(listOf("built 1", "built 2"), built)
    }

    @Test
    fun anUnchangedKeyDoesNot() {
        val root = FakeNode()
        var computed = 0
        val content: context(Composer)
        () -> Unit = {
            node(Parent, content = { remember<Int>("stable") { computed++ } })
        }

        pass(root, content)
        pass(root, content)

        assertEquals(1, computed)
    }

    @Test
    fun eachNodeRemembersSeparately() {
        // Positional identity is per parent, so two siblings at the same inner index must not share.
        val root = FakeNode()
        val seen = mutableListOf<Int>()
        var next = 0
        val content: context(Composer)
        () -> Unit = {
            repeat(2) { node(Parent, content = { seen += remember { next++ } }) }
        }

        pass(root, content)
        pass(root, content)

        assertEquals(listOf(0, 1, 0, 1), seen, "siblings shared one slot")
    }

    @Test
    fun aNodeThatLeavesTakesItsSlotsWithIt() {
        // The failure ui-core's string-keyed store had: an entry outliving the widget that wrote it,
        // then being adopted by whatever claimed the same key next.
        val root = FakeNode()
        var present = true
        var computed = 0
        val content: context(Composer)
        () -> Unit = {
            if (present) node(Parent, content = { remember<Int> { computed++ } })
        }

        pass(root, content)
        present = false
        pass(root, content)
        present = true
        pass(root, content)

        assertEquals(2, computed, "the slot survived the node being removed")
    }

    @Test
    fun slotsThisPassStoppedDeclaringAreDropped() {
        val root = FakeNode()
        val kept = mutableListOf<String>()
        var both = true
        val content: context(Composer)
        () -> Unit = {
            node(
                Parent,
                content = {
                    val first: String = remember { "first" }
                    if (both) kept += first + remember { "second" } else kept += first
                },
            )
        }

        pass(root, content)
        both = false
        pass(root, content)

        assertEquals(1, root.children[0].rememberSlots.size)
        assertEquals(listOf("firstsecond", "first"), kept)
    }

    @Test
    fun nestedContentRemembersAgainstItsOwnNode() {
        val root = FakeNode()
        val seen = mutableListOf<String>()
        val content: context(Composer)
        () -> Unit = {
            node(
                Parent,
                content = {
                    seen += remember { "outer" }
                    node(Child, content = { seen += remember { "inner" } })
                },
            )
        }

        pass(root, content)

        val parent = root.children[0]
        assertEquals(listOf("outer", "inner"), seen)
        assertEquals(1, parent.rememberSlots.size, "the inner value landed on the outer node")
        assertEquals(1, parent.children[0].rememberSlots.size)
    }

    @Test
    fun theRootCanRememberWhenItIsPassedIn() {
        val root = FakeNode()
        var computed = 0
        val content: context(Composer)
        () -> Unit = { remember<Int> { computed++ } }

        pass(root, content)
        pass(root, content)

        assertEquals(1, computed)
        assertEquals(1, root.rememberSlots.size)
    }

    @Test
    fun rememberingWithNoNodeSaysSoRatherThanRecomputing() {
        // Silently recomputing would look like it worked and drop state every frame.
        val root = FakeNode()
        val failure = assertFailsWith<IllegalStateException> {
            reconcile(FakeApplier(root)) { remember<String> { "x" } }
        }

        assertEquals(true, failure.message?.contains("RememberHolder"))
    }

    @Test
    fun twoKeysRecomputeWhenEitherOneChanges() {
        // The case that needed this overload: a style reads a theme *and* switches on a variant, so
        // keying on either alone returns the other's last value.
        val root = FakeNode()
        var computed = 0
        var theme = "light"
        var variant = "default"
        val content: context(Composer)
        () -> Unit = { remember<Int>(theme, variant) { computed++ } }

        pass(root, content)
        pass(root, content)
        assertEquals(1, computed, "a pass with both keys unchanged recomputed")

        variant = "outline"
        pass(root, content)
        assertEquals(2, computed, "the second key changed and was not noticed")

        theme = "dark"
        pass(root, content)
        assertEquals(3, computed, "the first key changed and was not noticed")

        assertEquals(1, root.rememberSlots.size, "a recompute allocated a second slot")
    }

    @Test
    fun aSecondKeyOfNullIsStillCompared() {
        // `null` is a legal key, so it has to miss the slot when it changes to non-null rather than
        // reading as "no second key given".
        val root = FakeNode()
        var computed = 0
        var second: String? = null
        val content: context(Composer)
        () -> Unit = { remember<Int>("fixed", second) { computed++ } }

        pass(root, content)
        second = "now set"
        pass(root, content)

        assertEquals(2, computed, "null -> non-null did not recompute")
    }

    @Test
    fun oneKeyAndTwoKeyRemembersDoNotShareASlotShape() {
        // The single-key overload delegates with an internal sentinel as its second key. A caller
        // passing that sentinel's *value* by accident is impossible, but a single-key remember must
        // still not be invalidated by the sentinel comparing unequal to itself.
        val root = FakeNode()
        var computed = 0
        val content: context(Composer)
        () -> Unit = { remember<Int>("key") { computed++ } }

        pass(root, content)
        pass(root, content)
        pass(root, content)

        assertEquals(1, computed, "the single-key overload recomputed after delegating")
    }

    private fun pass(root: FakeNode, content: context(Composer) () -> Unit) {
        reconcile(FakeApplier(root), root, content)
    }
}
