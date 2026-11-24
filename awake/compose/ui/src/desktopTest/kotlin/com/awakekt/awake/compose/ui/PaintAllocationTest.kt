/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.graphics.drawscope.PaintScope
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.GlyphDecoration
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.testing.allocation.assertAllocatesAtMost
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Gates per-frame allocations inside [Painter]'s `DrawModifierNode` chain walk and [PaintScope]'s
 * decorated glyph painting (#1742, #1512).
 */
class PaintAllocationTest {
    private class NoOpDrawNode :
        Modifier.Node(),
        DrawModifierNode {
        override fun DrawScope.draw(drawContent: () -> Unit) {
            drawContent()
        }
    }

    private object NoOpDrawElement : ModifierNodeElement<NoOpDrawNode>() {
        override fun create(): NoOpDrawNode = NoOpDrawNode()
        override fun update(node: NoOpDrawNode) = Unit
        override fun toString(): String = "noOpDraw()"
    }

    @Test
    fun paintingDrawModifierChainsAllocatesNothingPerNode() {
        val painter = Painter()
        val root = LayoutNode(ColumnMeasurePolicy())
        val chain = NoOpDrawElement then NoOpDrawElement then NoOpDrawElement
        composeInto(root) {
            Box {
                repeat(NODES) { Box(chain) }
            }
        }
        root.layoutTree(Constraints.fixed(WIDTH, HEIGHT))

        repeat(WARMUP) { painter.paint(root) }
        val reading = readAllocation(unitsPerWindow = FRAMES) {
            repeat(FRAMES) { painter.paint(root) }
        }
        // Only the single PaintResult wrapper per frame (~24 B) may allocate; a per-link lambda
        // across 600 links allocates ~14,424 B/frame.
        assertAllocatesAtMost(reading, CHAIN_WALK_CEILING, "Painter draw-modifier chain walk over ${NODES * 3} links")
    }

    @Test
    fun clippedDecoratedGlyphAllocatesNothing() {
        val scope = PaintScope()
        val node = LayoutNode(ColumnMeasurePolicy()).also { it.layoutTree(Constraints.fixed(100, 100)) }
        val font = UiFonts.default()
        val decoration = sampleDecoration()

        fun paintClippedOut() {
            scope.reset()
            scope.enter(node)
            scope.clipped(0f) {
                scope.withAlpha(0.5f) {
                    scope.withScale(1.5f, 1.5f) {
                        repeat(64) { i ->
                            scope.drawDecoratedGlyph(
                                x = 500f + i * 10f,
                                y = 500f,
                                width = 12f,
                                height = 16f,
                                u0 = 0f,
                                v0 = 0f,
                                u1 = 0.1f,
                                v1 = 0.1f,
                                color = Color.Transparent,
                                font = font,
                                page = 0,
                                decoration = decoration,
                            )
                        }
                    }
                }
            }
        }

        // Clipped primitives still push/pop one ClipPush + ClipPop inside `clipped`; isolate the glyph loop itself.
        scope.reset()
        scope.enter(node)
        scope.clipped(0f) {
            scope.withAlpha(0.5f) {
                scope.withScale(1.5f, 1.5f) {
                    repeat(WARMUP) {
                        scope.drawDecoratedGlyph(
                            x = 500f,
                            y = 500f,
                            width = 12f,
                            height = 16f,
                            u0 = 0f,
                            v0 = 0f,
                            u1 = 0.1f,
                            v1 = 0.1f,
                            color = Color.Transparent,
                            font = font,
                            page = 0,
                            decoration = decoration,
                        )
                    }
                    val reading = readAllocation(unitsPerWindow = FRAMES) {
                        repeat(FRAMES) {
                            scope.drawDecoratedGlyph(
                                x = 500f,
                                y = 500f,
                                width = 12f,
                                height = 16f,
                                u0 = 0f,
                                v0 = 0f,
                                u1 = 0.1f,
                                v1 = 0.1f,
                                color = Color.Transparent,
                                font = font,
                                page = 0,
                                decoration = decoration,
                            )
                        }
                    }
                    assertAllocatesNothing(reading, "clipped-out drawDecoratedGlyph under scale and alpha")
                }
            }
        }
        paintClippedOut()
        assertEquals(2, scope.primitives().size, "only ClipPush and ClipPop should be emitted")
    }

    @Test
    fun decoratedGlyphCacheReusesScaledAndDimmedDecorationAndEvictsUnusedEntries() {
        val scope = PaintScope()
        val node = LayoutNode(ColumnMeasurePolicy()).also { it.layoutTree(Constraints.fixed(WIDTH, HEIGHT)) }
        val font = UiFonts.default()
        val decoration = sampleDecoration()

        fun paintOneFrame(drawGlyph: Boolean): UiDrawPrimitive.Glyph? {
            scope.reset()
            scope.enter(node)
            if (drawGlyph) {
                scope.withAlpha(0.5f) {
                    scope.withScale(1.5f, 1.5f) {
                        scope.drawDecoratedGlyph(
                            x = 20f,
                            y = 20f,
                            width = 12f,
                            height = 16f,
                            u0 = 0f,
                            v0 = 0f,
                            u1 = 0.1f,
                            v1 = 0.1f,
                            color = Color.Transparent,
                            font = font,
                            page = 0,
                            decoration = decoration,
                        )
                    }
                }
            }
            return scope.primitives().firstOrNull() as? UiDrawPrimitive.Glyph
        }

        val frame1 = assertNotNull(paintOneFrame(drawGlyph = true))
        val frame2 = assertNotNull(paintOneFrame(drawGlyph = true))
        assertSame(Color.Transparent, frame1.color, "transparent fill must not allocate a dimmed Color copy")
        assertSame(frame1.decoration, frame2.decoration, "second frame with same scale and alpha reuses cached decoration")
        assertEquals(decoration.outlineWidth * 1.5f, assertNotNull(frame1.decoration).outlineWidth)
        assertEquals(decoration.outlineColor.a * 0.5f, assertNotNull(frame1.decoration).outlineColor.a)

        // Two frames without drawing this glyph evict it so disposed labels do not pin cache entries.
        paintOneFrame(drawGlyph = false)
        paintOneFrame(drawGlyph = false)
        val frameAfterEviction = assertNotNull(paintOneFrame(drawGlyph = true))
        assertNotSame(frame1.decoration, frameAfterEviction.decoration, "unused cache entry should be evicted after idle frame")
    }

    private fun sampleDecoration(): GlyphDecoration = GlyphDecoration(
        fieldOffsetX = -2f,
        fieldOffsetY = -2f,
        fieldW = 16f,
        fieldH = 20f,
        fieldU0 = 0f,
        fieldV0 = 0f,
        fieldU1 = 0.2f,
        fieldV1 = 0.2f,
        outlineColor = Color(0f, 0f, 0f, 1f),
        outlineWidth = 1.5f,
        shadowColor = Color(0f, 0f, 0f, 0.8f),
        shadowOffsetX = 1f,
        shadowOffsetY = 2f,
        shadowBlur = 2f,
    )

    private companion object {
        const val NODES = 200
        const val WIDTH = 800
        const val HEIGHT = 600
        const val WARMUP = 200
        const val FRAMES = 200
        const val CHAIN_WALK_CEILING = 200L
    }
}
