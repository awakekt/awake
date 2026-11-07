/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class Polled(var source: Int) : PolledState<Int>() {
    override fun read(): Int = source
}

class ReadTrackingTest {

    private val root = FakeNode()
    private val composition = Composition(FakeApplier(root), root)

    @Test
    fun aCompositionIsInvalidUntilItsFirstPass() {
        assertTrue(composition.isInvalid)
        composition.reconcile { }
        assertFalse(composition.isInvalid)
    }

    @Test
    fun aWriteToAStateThePassReadInvalidatesIt() {
        val count = mutableStateOf(0)
        composition.reconcile { count.value }

        count.value = 1

        assertTrue(composition.isInvalid)
    }

    @Test
    fun aWriteToAStateThePassDidNotReadDoesNot() {
        val read = mutableStateOf(0)
        val unread = mutableStateOf(0)
        composition.reconcile { read.value }

        unread.value = 1

        assertFalse(composition.isInvalid)
    }

    @Test
    fun aWriteOfAnEqualValueDoesNot() {
        val name = mutableStateOf("a")
        composition.reconcile { name.value }

        name.value = "a"

        assertFalse(composition.isInvalid)
    }

    @Test
    fun aReconcileClearsTheInvalidation() {
        val count = mutableStateOf(0)
        val seen = mutableListOf<Int>()
        composition.reconcile { seen += count.value }
        count.value = 1

        composition.reconcile { seen += count.value }

        assertFalse(composition.isInvalid)
        assertEquals(listOf(0, 1), seen)
    }

    @Test
    fun aStateTheLatestPassStoppedReadingNoLongerInvalidates() {
        val count = mutableStateOf(0)
        var readIt = true
        val content: context(Composer)
        () -> Unit = { if (readIt) count.value }
        composition.reconcile(content)
        readIt = false
        composition.reconcile(content)

        count.value = 1

        assertFalse(composition.isInvalid)
    }

    @Test
    fun aWriteAfterTheReadInTheSamePassLeavesItInvalid() {
        val count = mutableStateOf(0)
        composition.reconcile {
            count.value
            count.value = 1
        }

        assertTrue(composition.isInvalid)
    }

    @Test
    fun aWriteInvalidatesEveryCompositionThatReadIt() {
        val other = Composition(FakeApplier(FakeNode()))
        val count = mutableStateOf(0)
        composition.reconcile { count.value }
        other.reconcile { count.value }

        count.value = 1

        assertTrue(composition.isInvalid)
        assertTrue(other.isInvalid)
    }

    @Test
    fun aNestedPassRecordsItsReadsToItsOwnComposition() {
        val inner = Composition(FakeApplier(FakeNode()))
        val outerState = mutableStateOf(0)
        val innerState = mutableStateOf(0)
        composition.reconcile {
            outerState.value
            inner.reconcile { innerState.value }
        }

        innerState.value = 1

        assertTrue(inner.isInvalid)
        assertFalse(composition.isInvalid)
        outerState.value = 1
        assertTrue(composition.isInvalid)
    }

    @Test
    fun readsOutsideAPassAreNotTracked() {
        val count = mutableStateOf(0)
        composition.reconcile { }
        count.value

        count.value = 1

        assertFalse(composition.isInvalid)
    }

    @Test
    fun invalidateMarksThePassOutOfDate() {
        composition.reconcile { }

        composition.invalidate()

        assertTrue(composition.isInvalid)
    }

    @Test
    fun aDisposedCompositionIsNotReachedByWrites() {
        val count = mutableStateOf(0)
        composition.reconcile { count.value }
        composition.dispose()
        composition.reconcile { }

        count.value = 1

        assertFalse(composition.isInvalid)
    }

    @Test
    fun aPassThatThrowsStaysInvalid() {
        assertFailsWith<IllegalStateException> { composition.reconcile { error("boom") } }

        assertTrue(composition.isInvalid)
        composition.reconcile { }
        assertFalse(composition.isInvalid)
    }

    @Test
    fun aPolledStateThatChangesInvalidatesThePassThatReadIt() {
        val polled = Polled(1)
        composition.reconcile { polled.value }
        assertFalse(composition.isInvalid)

        polled.source = 2

        assertTrue(composition.isInvalid)
        polled.source = 1
        assertFalse(composition.isInvalid)
    }

    @Test
    fun aPolledStateThePassDidNotReadDoesNot() {
        val polled = Polled(1)
        composition.reconcile { }

        polled.source = 2

        assertFalse(composition.isInvalid)
    }
}
