/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that fading [CanvasElement.alpha] in a hot per-frame loop allocates zero heap bytes
 * under `-XX:-DoEscapeAnalysis`, replacing `element.style = element.style.copy(alpha = ...)`.
 */
class CanvasAlphaAllocationTest {
    @Test
    fun mutatingElementAlphaAllocatesNothingPerFrame() {
        val changes = CanvasChanges()
        val elements = List(MARKS) {
            CanvasElement().apply {
                kind = CanvasElementKind.Text
                text = "128"
                this.changes = changes
                alpha = 0.5f
            }
        }
        val steps = FloatArray(FRAMES) { ((it % 20) + 1) / 20f }

        var revSink = 0
        val reading = readAllocation(FRAMES) {
            for (frame in 0 until FRAMES) {
                val nextAlpha = steps[frame]
                for (i in 0 until MARKS) {
                    elements[i].alpha = nextAlpha
                }
                revSink = changes.revision
            }
        }
        println("CanvasAlphaAllocation ($MARKS elements): $reading (rev=$revSink)")
        assertAllocatesNothing(reading, "CanvasElement.alpha fade updates")
        assertEquals(steps[FRAMES - 1], elements[0].alpha)
    }

    private companion object {
        const val MARKS = 32
        const val FRAMES = 500
    }
}
