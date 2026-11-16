/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.PhaseReadCause
import com.awakekt.awake.compose.runtime.PolledState
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [ComposeHost.explainMeasure] names why a frame whose composition was skipped measured anyway, and
 * which layout node's measure read what changed (#1512): the question a HUD that measures every frame
 * cannot otherwise answer.
 */
class MeasureCauseTest {
    private val host = ComposeHost().also {
        it.skipUnchangedComposition = true
        it.skipUnchangedMeasure = true
    }
    private val input = FrameInput(800, 600, deltaSeconds = 0f)

    // Read only while measuring: a write changes no composition, only the measure that read it.
    private val width = mutableStateOf(10)
    private val polledWidth = object : PolledState<Int>() {
        var next = 10
        override fun read(): Int = next
    }
    private val byState = MeasurePolicy { _, _ -> layout(width.value, 10) {} }
    private val byPoll = MeasurePolicy { _, _ -> layout(polledWidth.value, 10) {} }

    private val content: context(Composer)
    () -> Unit = {
        Box {
            Layout(nodeType = ByState, measurePolicy = byState)
            Layout(nodeType = ByPoll, measurePolicy = byPoll)
        }
    }

    @Test
    fun aStateWrittenSinceTheLastMeasureIsNamedWithTheNodeThatReadIt() {
        host.explainMeasure = true
        settle()

        width.value = 20
        host.frame(input, content)

        val counts = host.frameCounts
        assertFalse(counts.composed, "the write changed no composition: $counts")
        assertTrue(counts.measured, "but the measure that read it runs again: $counts")
        assertEquals(PhaseReadCause.StateWritten, counts.measureCause)
        assertSame(width, counts.measureCauseSource)
        assertSame(byState, (counts.measureCauseOwner as LayoutNode).measurePolicy, "the node whose measure read it")
    }

    @Test
    fun aPolledValueThatChangedIsNamedWithTheNodeThatReadIt() {
        host.explainMeasure = true
        settle()

        polledWidth.next = 30
        host.frame(input, content)

        val counts = host.frameCounts
        assertTrue(counts.measured)
        assertEquals(PhaseReadCause.PolledChanged, counts.measureCause)
        assertSame(polledWidth, counts.measureCauseSource)
        assertSame(byPoll, (counts.measureCauseOwner as LayoutNode).measurePolicy)
    }

    @Test
    fun aSkippedMeasureAndAComposedFrameHaveNoCause() {
        host.explainMeasure = true
        host.frame(input, content)
        assertEquals(PhaseReadCause.None, host.frameCounts.measureCause, "a frame that composed measures because it composed")

        settle()

        assertFalse(host.frameCounts.measured)
        assertEquals(PhaseReadCause.None, host.frameCounts.measureCause)
    }

    @Test
    fun whileNotExplainingNothingIsRecorded() {
        settle()

        width.value = 40
        host.frame(input, content)

        assertTrue(host.frameCounts.measured)
        assertEquals(PhaseReadCause.None, host.frameCounts.measureCause)
        assertEquals(null, host.frameCounts.measureCauseOwner)
    }

    /** Frames until one composes and measures nothing. */
    private fun settle() {
        repeat(3) { host.frame(input, content) }
    }

    private object ByState
    private object ByPoll
}
