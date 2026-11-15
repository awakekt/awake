/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.TranslatePop
import com.awakekt.awake.core.graphics2d.TranslatePush
import com.awakekt.awake.core.graphics2d.flattened
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A subtree that moved as a whole hands on its primitives unchanged inside a translate range. The
 * staging moves them as it stages, so a range stages what moved copies of its primitives would.
 */
class TranslatedStagingTest {
    private val frame = DrawCommand.Quad(10f, 20f, 70f, 20f, Color(0.1f, 0.1f, 0.1f, 1f))
    private val bar = DrawCommand.RoundedQuad(12f, 22f, 36f, 4f, Color(0.2f, 0.8f, 0.2f, 1f), radius = 2f)
    private val letters = List(8) { DrawCommand.Glyph(14f + it * 6.5f, 30.25f, 6f, 9f, 0f, 0f, 1f, 1f, Color.White) }
    private val nameplate = listOf(frame, bar) + letters + DrawCommand.ClipPush(Rectangle(10f, 20f, 70f, 20f)) +
        DrawCommand.ClipPop(Rectangle(-1e9f, -1e9f, 2e9f, 2e9f))

    private fun moved(dx: Float, dy: Float): List<DrawCommand> = listOf(TranslatePush(dx, dy)) + nameplate + TranslatePop

    @Test
    fun aRangeStagesWhatMovedCopiesOfItsPrimitivesStage() {
        val range = moved(37f, -12f)

        assertStagedAlike(DrawRunCoalescer.coalesce(range.flattened()), DrawRunCoalescer.coalesce(range))
    }

    @Test
    fun nestedRangesAddTheirOffsets() {
        val nested = listOf(TranslatePush(5f, 5f), frame, TranslatePush(30f, -2f)) + letters + TranslatePop + TranslatePop

        assertStagedAlike(DrawRunCoalescer.coalesce(nested.flattened()), DrawRunCoalescer.coalesce(nested))
    }

    /**
     * The same primitives at a new offset each frame, through the retained cache and the pool as a
     * renderer stages them: each frame stages where the range is now, never a cached earlier place.
     */
    @Test
    fun theSamePrimitivesAtANewOffsetEachFrameAreNeverStagedFromAnEarlierFrame() {
        val retained = RetainedDrawRunCache()
        val pool = DynamicRunArrayPool()
        for (step in 0 until 6) {
            val range = moved(step * 3f, step * 2f)
            val staged = DrawRunCoalescer.coalesce(range, retained = retained, pool = pool)

            assertStagedAlike(DrawRunCoalescer.coalesce(range.flattened()), staged)
        }
    }

    @Test
    fun aRangeLeftOpenDoesNotMoveTheNextList() {
        DrawRunCoalescer.coalesce(listOf(TranslatePush(50f, 50f), frame))

        assertStagedAlike(DrawRunCoalescer.coalesce(listOf(frame)), DrawRunCoalescer.coalesce(listOf<DrawCommand>(frame)))
        assertEquals(10f, (DrawRunCoalescer.coalesce(listOf(frame)).single() as StagedDrawRun.QuadRun).vertices[0])
    }

    @Test
    fun aPrimitiveThatBakesItsPositionInCannotSitInARange() {
        val scaled = frame.copy(transform = com.awakekt.awake.core.graphics2d.DrawTransform(scaleX = 2f, scaleY = 2f, pivotX = 10f, pivotY = 20f))

        assertFailsWith<IllegalStateException> { DrawRunCoalescer.coalesce(listOf(TranslatePush(1f, 1f), scaled, TranslatePop)) }
    }

    private fun assertStagedAlike(expected: List<StagedDrawRun>, actual: List<StagedDrawRun>) {
        assertEquals(expected.map { it::class }, actual.map { it::class })
        for (index in expected.indices) {
            val want = expected[index]
            val got = actual[index]
            when (want) {
                is StagedDrawRun.ClipRun -> assertEquals(want.rect, (got as StagedDrawRun.ClipRun).rect)
                else -> assertNear(verticesOf(want), verticesOf(got))
            }
        }
    }

    private fun verticesOf(run: StagedDrawRun): FloatArray = when (run) {
        is StagedDrawRun.QuadRun -> run.vertices
        is StagedDrawRun.RoundedQuadRun -> run.vertices
        is StagedDrawRun.GlyphRun -> run.vertices
        else -> error("unexpected $run")
    }

    // Moving a staged position adds the offset once to x + w, where a moved copy adds it to x
    // first: equal to the last bit at whole-pixel offsets, and within one rounding step otherwise.
    private fun assertNear(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        for (index in expected.indices) {
            assertTrue(abs(expected[index] - actual[index]) <= 1e-3f, "float $index: ${expected[index]} against ${actual[index]}")
        }
    }
}
