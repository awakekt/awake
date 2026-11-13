/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.interaction.Interaction
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An [InteractionSource] costs what it did before read tracking existed (#1512, stage a).
 *
 * A host that does not skip unchanged composition passes must not pay for tracking: creating a source
 * allocates one object of 24 bytes (three counts), and reading or emitting allocates nothing. The
 * tracking state exists only for a pass that asked for it. A plugin's install benchmark creates
 * many sources, so a few bytes more per source shows up there.
 */
class InteractionSourceAllocationTest {
    @Test
    fun creatingSourcesAllocatesWhatItDidBeforeTracking() {
        val sources = arrayOfNulls<InteractionSource>(COUNT)
        repeat(WARMUP) { InteractionSource() }
        val before = allocatedBytes()
        for (i in 0 until COUNT) sources[i] = InteractionSource()
        val perSource = (allocatedBytes() - before) / COUNT
        println("InteractionSource: $perSource B per source, array slot included")
        assertTrue(perSource <= SOURCE_BYTES, "a source allocated $perSource B, over $SOURCE_BYTES B")
    }

    @Test
    fun readingAndEmittingAllocateNothingWithoutATrackingPass() {
        val source = InteractionSource()
        val hover = Interaction.Hover.Enter
        val exit = Interaction.Hover.Exit(hover)
        repeat(WARMUP) { source.isHovered; source.tryEmit(hover); source.tryEmit(exit) }
        val before = allocatedBytes()
        var hovered = 0
        repeat(COUNT) {
            source.tryEmit(hover)
            if (source.isHovered) hovered++
            source.tryEmit(exit)
        }
        val bytes = allocatedBytes() - before
        assertEquals(COUNT, hovered)
        assertTrue(bytes <= SLACK_BYTES, "reads and emits allocated $bytes B")
    }

    @Test
    fun countsStillPairUpAndIgnoreUnmatchedEnds() {
        val source = InteractionSource()
        source.tryEmit(Interaction.Hover.Exit(Interaction.Hover.Enter))
        assertTrue(!source.isHovered)
        val press = Interaction.Press.Press
        source.tryEmit(press)
        source.tryEmit(press)
        source.tryEmit(Interaction.Press.Release(press))
        assertTrue(source.isPressed)
        source.reset()
        assertTrue(!source.isPressed)
    }

    private fun allocatedBytes(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val COUNT = 20_000
        const val WARMUP = 5_000

        // What main allocates per source, measured with the array slot included.
        const val SOURCE_BYTES = 24
        const val SLACK_BYTES = 2_000
    }
}
