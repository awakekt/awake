/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics.vector

import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.UiFillRule
import com.awakekt.awake.core.graphics2d.UiStrokeCap
import com.awakekt.awake.core.graphics2d.UiStrokeJoin
import com.awakekt.awake.core.math2d.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [packedImageVector] decodes to exactly what the builder form of the same icon builds.
 *
 * Both halves of every pair below are generated output -- `svg_to_ui_image_vector.py` run over one
 * fixture SVG with and without `--packed`. That is the whole contract: the packed string is a
 * transport for the builder calls, so a decoder that drifts from the emitter is the only way an
 * icon can silently change shape, and this is where that shows up.
 *
 * The fixtures are chosen for the encodings that are easy to get wrong rather than for looking like
 * icons: a negative coordinate written with no separator before it (`C4-1.5`), a repeated command
 * whose letter is dropped (`L9 7.5 9 13.5`), a second `moveTo` inside one path, quadratic and cubic
 * segments in the same path, an even-odd path with nested subpaths, and a stroked path whose cap
 * and join are packed as single letters.
 */
class PackedImageVectorParityTest {

    /** Fill and stroke in one icon, with curves, a negative operand, and repeated commands. */
    @Test
    fun packedMixedShapesMatchesTheBuilderForm() {
        val packed = packedImageVector(
            "24 24 24 24|f:M-2 3.25C4-1.5 8.75 2 12 6Q14.5 9.25 16 12C16 13.6569 14.6569 15 13 15 " +
                "11.3431 15 10 13.6569 10 12Z|s1.5rr:M3 7.5L9 7.5 9 13.5M12 16C13 17 15 18 17 16",
        )

        val built = imageVector(
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            path {
                moveTo(-2f, 3.25f)
                cubicTo(4f, -1.5f, 8.75f, 2f, 12f, 6f)
                quadTo(14.5f, 9.25f, 16f, 12f)
                cubicTo(16f, 13.6569f, 14.6569f, 15f, 13f, 15f)
                cubicTo(11.3431f, 15f, 10f, 13.6569f, 10f, 12f)
                close()
            }
            path(stroke = DrawStroke(width = 1.5f.dp, cap = UiStrokeCap.Round, join = UiStrokeJoin.Round)) {
                moveTo(3f, 7.5f)
                lineTo(9f, 7.5f)
                lineTo(9f, 13.5f)
                moveTo(12f, 16f)
                cubicTo(13f, 17f, 15f, 18f, 17f, 16f)
            }
        }

        assertEquals(built, packed)
    }

    /** Even-odd with nested subpaths -- the hole encoding every ring-shaped icon depends on. */
    @Test
    fun packedNestedRingMatchesTheBuilderForm() {
        val packed = packedImageVector("20 20 20 20|e:M2 2L18 2 18 18 2 18ZM6 6L6 14 14 14 14 6Z")

        val built = imageVector(
            defaultWidth = 20f.dp,
            defaultHeight = 20f.dp,
            viewportWidth = 20f,
            viewportHeight = 20f,
        ) {
            path(fillRule = UiFillRule.EvenOdd) {
                moveTo(2f, 2f)
                lineTo(18f, 2f)
                lineTo(18f, 18f)
                lineTo(2f, 18f)
                close()
                moveTo(6f, 6f)
                lineTo(6f, 14f)
                lineTo(14f, 14f)
                lineTo(14f, 6f)
                close()
            }
        }

        assertEquals(built, packed)
    }

    /**
     * A malformed string fails at the icon that carries it, not silently as a missing shape.
     *
     * The reason `packedImageVector` is worth having at all is that the reference stays a compile-time
     * symbol; that only holds if bad data is loud. An unknown command letter must throw rather than
     * decode to a shorter path.
     */
    @Test
    fun packedGarbageThrows() {
        assertFailsWith<IllegalStateException> { packedImageVector("20 20 20 20|f:M1 1X9 9") }
        assertFailsWith<IllegalStateException> { packedImageVector("20 20 20 20|x:M1 1") }
        assertFailsWith<IllegalArgumentException> { packedImageVector("20 20 20|f:M1 1") }
        assertFailsWith<IllegalArgumentException> { packedImageVector("20 20 20 20|M1 1") }
    }
}
