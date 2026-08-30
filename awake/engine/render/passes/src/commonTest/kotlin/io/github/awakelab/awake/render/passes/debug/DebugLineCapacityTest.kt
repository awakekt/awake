/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.passes.debug

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The growth rule lives in the shared layout so both backends obey one copy of it, which also
 * means it is testable without a GPU. `LineMesh` allocation is per-backend; this arithmetic —
 * the part that decides whether a frame renders or throws — is not.
 */
class DebugLineCapacityTest {
    private val perLine = DebugLineLayout.VERTICES_PER_LINE

    @Test
    fun keepsCapacityWhenTheFrameAlreadyFits() {
        val current = 64 * perLine

        assertEquals(current, DebugLineLayout.grownVertexCapacity(current, current))
        assertEquals(current, DebugLineLayout.grownVertexCapacity(current, current - perLine))
    }

    @Test
    fun doublesUntilTheFrameFits() {
        val current = 64 * perLine

        // The case that started this: 74 lines against a 64-line buffer.
        assertEquals(128 * perLine, DebugLineLayout.grownVertexCapacity(current, 74 * perLine))
        // Several doublings at once rather than one step per call.
        assertEquals(1024 * perLine, DebugLineLayout.grownVertexCapacity(current, 700 * perLine))
    }

    @Test
    fun growsToAPowerOfTwoMultipleOfTheStartingSize() {
        val current = 64 * perLine
        for (lines in listOf(65, 100, 128, 129, 500)) {
            val grown = DebugLineLayout.grownVertexCapacity(current, lines * perLine)
            assertTrue(grown >= lines * perLine, "$lines lines did not fit in $grown vertices.")
            assertEquals(0, grown % current, "Capacity $grown is not a doubling of $current.")
        }
    }

    /** A zero-line frame is a legitimate "nothing to draw", not a reason to reallocate. */
    @Test
    fun leavesCapacityAloneForAnEmptyFrame() {
        val current = 64 * perLine

        assertEquals(current, DebugLineLayout.grownVertexCapacity(current, 0))
    }

    /**
     * Growing forever would turn a producer bug into an out-of-memory far from its cause. The
     * ceiling keeps the loud failure that caught the navigation grid overflow in the first place.
     */
    @Test
    fun refusesToGrowPastTheCeiling() {
        val tooMany = (DebugLineLayout.MAX_LINES_CEILING + 1) * perLine

        val error = assertFailsWith<IllegalArgumentException> {
            DebugLineLayout.grownVertexCapacity(64 * perLine, tooMany)
        }
        assertTrue(
            error.message.orEmpty().contains("runaway producer"),
            "The message should name the cause, not just the number: ${error.message}",
        )
    }

    @Test
    fun acceptsExactlyTheCeiling() {
        val atCeiling = DebugLineLayout.MAX_LINES_CEILING * perLine

        assertEquals(atCeiling, DebugLineLayout.grownVertexCapacity(64 * perLine, atCeiling))
    }
}
