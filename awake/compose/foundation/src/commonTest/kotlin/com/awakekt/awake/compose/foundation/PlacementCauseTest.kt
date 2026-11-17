/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.PhaseReadCause
import com.awakekt.awake.compose.runtime.PolledState
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.onPlaced
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * [ComposeHost.explainPlacement] names what placement changed that composition read (#1512).
 *
 * Such a frame composes and lays out twice ([ComposeHost.placementRecompositions]), and a host that
 * skips unchanged frames never skips it. A host saw every frame do so once a canvas was drawn, with
 * nothing on the frame to say which write was responsible: the count says that it happened, this
 * says what did it.
 */
class PlacementCauseTest {
    private val placedFrame = mutableStateOf(0)
    private val polled = Counter()
    private var frame = 0

    /** A polled source whose value [bump] moves, read without being tracked. */
    private class Counter : PolledState<Int>() {
        var count = 0

        override fun read(): Int = count
    }

    context(_: Composer)
    private fun WritesOnPlacement(state: Boolean, source: Boolean) {
        // Read in composition, changed by placement below: the host composes and lays out again.
        placedFrame.value
        polled.value
        Box(
            Modifier.size(10.dp).onPlaced { _, _, _, _ ->
                if (state) placedFrame.value = frame
                if (source) polled.count = frame
            },
        )
    }

    private fun host(explain: Boolean) = ComposeHost().apply {
        skipUnchangedComposition = true
        explainPlacement = explain
    }

    private fun ComposeHost.frames(count: Int, state: Boolean, source: Boolean) = repeat(count) {
        frame++
        frame(FrameInput(200, 100)) { WritesOnPlacement(state, source) }
    }

    @Test
    fun aStateWrittenWhilePlacingIsNamed() {
        val host = host(explain = true)
        host.frames(FRAMES, state = true, source = false)
        assertEquals(FRAMES, host.placementRecompositions, "every frame composed twice")
        assertEquals(PhaseReadCause.StateWritten, host.frameCounts.placementCause)
        assertSame(placedFrame, host.frameCounts.placementCauseSource, "the state placement wrote")
    }

    @Test
    fun aPolledSourceThatMovedWhilePlacingIsNamed() {
        val host = host(explain = true)
        host.frames(FRAMES, state = false, source = true)
        assertEquals(FRAMES, host.placementRecompositions, "every frame composed twice")
        assertEquals(PhaseReadCause.PolledChanged, host.frameCounts.placementCause)
        assertSame(polled, host.frameCounts.placementCauseSource, "the source that moved")
    }

    @Test
    fun aFrameWhosePlacementChangesNothingHasNoCause() {
        val host = host(explain = true)
        host.frames(FRAMES, state = false, source = false)
        assertEquals(0, host.placementRecompositions)
        assertEquals(PhaseReadCause.None, host.frameCounts.placementCause)
        assertNull(host.frameCounts.placementCauseSource)
    }

    @Test
    fun withoutExplainingNothingIsRecorded() {
        val host = host(explain = false)
        host.frames(FRAMES, state = true, source = false)
        assertEquals(FRAMES, host.placementRecompositions, "the frames still compose twice")
        assertEquals(PhaseReadCause.None, host.frameCounts.placementCause)
        assertNull(host.frameCounts.placementCauseSource)
    }

    private companion object {
        const val FRAMES = 5
    }
}
