// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private val localLabel = compositionLocalOf { "default" }
private val localCount = compositionLocalOf { 0 }

class CompositionLocalTest {

    private fun compose(content: context(Composer) () -> Unit) {
        reconcile(NoOpApplier(), content)
    }

    @Test
    fun readingWithoutAProviderYieldsTheDefault() {
        var seen = ""
        compose { seen = localLabel.current }

        assertEquals("default", seen)
    }

    @Test
    fun aProviderSuppliesItsSubtree() {
        var seen = ""
        compose {
            CompositionLocalProvider(localLabel, "provided") {
                seen = localLabel.current
            }
        }

        assertEquals("provided", seen)
    }

    @Test
    fun theInnermostProviderWins() {
        val seen = mutableListOf<String>()
        compose {
            CompositionLocalProvider(localLabel, "outer") {
                seen += localLabel.current
                CompositionLocalProvider(localLabel, "inner") {
                    seen += localLabel.current
                }
                seen += localLabel.current
            }
        }

        assertEquals(listOf("outer", "inner", "outer"), seen, "the window closes on exit")
    }

    @Test
    fun theValueIsGoneAfterTheProviderReturns() {
        var after = ""
        compose {
            CompositionLocalProvider(localLabel, "provided") {}
            after = localLabel.current
        }

        assertEquals("default", after)
    }

    @Test
    fun independentLocalsDoNotShadowEachOther() {
        var label = ""
        var count = -1
        compose {
            CompositionLocalProvider(localLabel, "a") {
                CompositionLocalProvider(localCount, 7) {
                    label = localLabel.current
                    count = localCount.current
                }
            }
        }

        assertEquals("a", label)
        assertEquals(7, count)
    }

    @Test
    fun aThrowInsideAProviderStillClosesTheWindow() {
        // The failure a hand-written push/pop pair has: ui-core's pushLocal/popLocal leaks its
        // value if the content between them throws.
        var after = ""
        compose {
            assertFailsWith<IllegalStateException> {
                CompositionLocalProvider(localLabel, "provided") {
                    error("boom")
                }
            }
            after = localLabel.current
        }

        assertEquals("default", after)
    }

    @Test
    fun theDefaultIsComputedOnceNotPerRead() {
        // A default that allocates would otherwise allocate on every read, and reads are per-frame.
        var factoryCalls = 0
        val local = compositionLocalOf {
            factoryCalls++
            StringBuilder("x")
        }
        var first: StringBuilder? = null
        var second: StringBuilder? = null
        compose {
            first = local.current
            second = local.current
        }

        assertEquals(1, factoryCalls)
        assertSame(first, second)
    }
}

/** Locals are composer state, not tree state, so these need no nodes at all. */
private class NoOpApplier : Applier {
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
