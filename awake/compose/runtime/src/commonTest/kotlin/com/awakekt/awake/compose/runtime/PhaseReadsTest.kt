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

/** [PhaseReads] records what a phase outside composition read, and only inside its brackets (#1512). */
class PhaseReadsTest {
    private class Polled(var source: Int) : PolledState<Int>() {
        override fun read(): Int = source
    }

    private val reads = PhaseReads()

    private fun pass(block: () -> Unit) {
        reads.beginPass()
        reads.recording(block)
    }

    @Test
    fun itIsInvalidUntilItsFirstPass() {
        assertTrue(reads.isInvalid)
        pass { }
        assertFalse(reads.isInvalid)
    }

    @Test
    fun aWriteToAStateThePassReadInvalidatesIt() {
        val width = mutableStateOf(1)
        pass { width.value }

        width.value = 2

        assertTrue(reads.isInvalid)
    }

    @Test
    fun aReadOutsideTheBracketIsNotRecorded() {
        val offset = mutableStateOf(1)
        reads.beginPass()
        reads.recording { }
        offset.value // placement, after the bracket

        offset.value = 2

        assertFalse(reads.isInvalid)
    }

    @Test
    fun aPolledReadInvalidatesWhenItsSourceChanges() {
        val shown = Polled(1)
        pass { shown.value }
        assertFalse(reads.isInvalid)

        shown.source = 0

        assertTrue(reads.isInvalid)
    }

    @Test
    fun manyPolledReadsOfOneSourceKeepTheLatestValue() {
        val shown = Polled(1)
        pass {
            repeat(3) { shown.value }
            shown.source = 2
            shown.value
        }

        assertFalse(reads.isInvalid, "the latest value read is the one compared")
    }

    @Test
    fun aCompositionRunInsideThePassInvalidatesItWhileItIsInvalid() {
        val inner = Composition(FakeApplier(FakeNode()))
        val label = mutableStateOf("a")
        pass { inner.reconcile { label.value } }
        assertFalse(reads.isInvalid)

        label.value = "b"

        assertTrue(reads.isInvalid, "a subcomposition that would compose again must make the phase run again")
    }

    @Test
    fun plainHoldersAreAskedToTrackWhileRecording() {
        var tracked = false
        pass { tracked = readsAreTracked }
        assertTrue(tracked)
        assertFalse(readsAreTracked, "outside the bracket nothing is tracked")
    }

    @Test
    fun aPassThatThrowsStaysInvalidAndRestoresTracking() {
        assertFailsWith<IllegalStateException> { pass { error("measure failed") } }

        assertTrue(reads.isInvalid)
        assertEquals(false, readsAreTracked)
    }

    @Test
    fun invalidateMarksTheLatestPassOutOfDate() {
        pass { }

        reads.invalidate()

        assertTrue(reads.isInvalid)
    }
}
