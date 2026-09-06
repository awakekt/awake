/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.PathCommand
import kotlin.test.Test
import kotlin.test.assertTrue
import com.awakekt.awake.core.graphics2d.drawPath as buildPath

/**
 * `drawPath` places a vector at its own node, which is the one thing `emit` does not do.
 *
 * Found by rendering a gallery: an icon declared last in a Column drew above the title it followed.
 * Every other `DrawScope` helper maps node-local coordinates into tree space; `emit` applies alpha
 * and nothing else, so a caller reaching for the escape hatch paints at the frame origin.
 */
class DrawPathPlacementTest {

    private val square = buildPath {
        moveTo(0f, 0f)
        lineTo(10f, 0f)
        lineTo(10f, 10f)
        lineTo(0f, 10f)
        close()
    }

    private fun yRange(primitives: List<DrawCommand>): ClosedFloatingPointRange<Float> {
        val ys = primitives.filterIsInstance<DrawCommand.FilledPath>()
            .flatMap { it.path.commands }
            .mapNotNull { c ->
                when (c) {
                    is PathCommand.MoveTo -> c.y
                    is PathCommand.LineTo -> c.y
                    else -> null
                }
            }
        return ys.min()..ys.max()
    }

    @Test
    fun aPathIsPlacedAtItsOwnNodeNotTheFrameOrigin() {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Column {
                Spacer(Modifier.size(40.dp))
                Spacer(Modifier.size(20.dp).drawBehind { drawPath(square, Color.White) })
            }
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val drawn = yRange(Painter().paint(root))

        assertTrue(
            drawn.start >= 40f,
            "the path drew at ${drawn.start}, above its own node -- the 40dp sibling precedes it",
        )
    }

    @Test
    fun emitStillTakesTreeSpaceSoTheTwoAreNotInterchangeable() {
        // Pinned deliberately: `emit` is the raw hatch and does not map, which is now documented
        // rather than surprising. A future change making it map would break callers that already
        // position their own primitives.
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Column {
                Spacer(Modifier.size(40.dp))
                Spacer(
                    Modifier.size(20.dp).drawBehind {
                        emit(DrawCommand.FilledPath(square, Color.White))
                    },
                )
            }
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val drawn = yRange(Painter().paint(root))

        assertTrue(drawn.start < 40f, "emit began mapping node-local coordinates")
    }
}
