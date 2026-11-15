/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.compose

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.render.testing.NoopRenderer
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Compositing a frame with no layers and nothing to resolve allocates the same few bytes however
 * many primitives it holds (#1621). Wrapping each primitive in a list of its own cost about 39 B per
 * primitive: 81,728 B at 2,000 primitives and 195,400 B at 5,000.
 *
 * Desktop only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class CompositeAllocationTest {
    private val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val compositor = GraphicsLayerCompositor()
    private val renderer = NoopRenderer()
    private val font = UiFonts.default()

    private fun quads(count: Int) = List(count) { UiDrawPrimitive.Quad(it.toFloat(), 0f, 4f, 4f, Color(1f, 1f, 1f, 1f)) }

    /** The quietest window's bytes per composite of [primitives]. */
    private fun bytesPerComposite(primitives: List<UiDrawPrimitive>): Long {
        repeat(WARMUP) { compositor.composite(renderer, primitives, emptyList(), font, WIDTH, HEIGHT) }
        var quietest = Long.MAX_VALUE
        repeat(WINDOWS) {
            val start = bean.currentThreadAllocatedBytes
            repeat(PER_WINDOW) { compositor.composite(renderer, primitives, emptyList(), font, WIDTH, HEIGHT) }
            quietest = minOf(quietest, (bean.currentThreadAllocatedBytes - start) / PER_WINDOW)
        }
        return quietest
    }

    @Test
    fun aFrameWithNothingToResolveCostsTheSameWhateverItsPrimitiveCount() {
        val small = bytesPerComposite(quads(SMALL))
        val large = bytesPerComposite(quads(LARGE))
        println("CompositeAllocation: $SMALL primitives $small B, $LARGE primitives $large B")
        assertTrue(large - small < PER_PRIMITIVE_SLACK * (LARGE - SMALL), "$large B at $LARGE against $small B at $SMALL grows with the primitives")
        assertTrue(large <= CEILING_BYTES, "$large B a composite, over the $CEILING_BYTES B ceiling")
    }

    private companion object {
        const val SMALL = 2_000
        const val LARGE = 5_000
        const val WIDTH = 800
        const val HEIGHT = 600
        const val WARMUP = 300
        const val WINDOWS = 20
        const val PER_WINDOW = 50

        /** Well under one object per primitive: a regression to a list per primitive is about 39. */
        const val PER_PRIMITIVE_SLACK = 1
        const val CEILING_BYTES = 1_024L
    }
}
