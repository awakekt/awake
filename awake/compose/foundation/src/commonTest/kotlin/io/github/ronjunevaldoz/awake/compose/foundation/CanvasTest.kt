// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.foundation.layout.Column
import io.github.ronjunevaldoz.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Spacer
import io.github.ronjunevaldoz.awake.compose.foundation.layout.height
import io.github.ronjunevaldoz.awake.compose.foundation.layout.padding
import io.github.ronjunevaldoz.awake.compose.foundation.layout.size
import io.github.ronjunevaldoz.awake.compose.foundation.layout.width
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.graphics.Painter
import io.github.ronjunevaldoz.awake.compose.ui.layout.composeInto
import io.github.ronjunevaldoz.awake.compose.ui.layout.layoutTree
import io.github.ronjunevaldoz.awake.compose.ui.layout.onPlaced
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val red = Color(1f, 0f, 0f, 1f)

private fun laidOut(content: context(Composer) () -> Unit): LayoutNode {
    val root = LayoutNode(ColumnMeasurePolicy())
    composeInto(root, content)
    root.layoutTree(Constraints.of(0, 300, 0, 300))
    return root
}

class CanvasTest {

    @Test
    fun aSpacerTakesTheSpaceItsModifierAsks() {
        val root = laidOut { Spacer(Modifier.size(40.dp)) }

        assertEquals(40, root.children[0].width)
        assertEquals(40, root.children[0].height)
    }

    @Test
    fun aSpacerWithNoModifierOccupiesNothing() {
        // It sizes to its minimum, so the modifier decides -- the widget has no opinion of its own.
        val root = laidOut { Spacer() }

        assertEquals(0, root.children[0].width)
    }

    @Test
    fun canvasDrawsWhatTheLambdaEmits() {
        val root = laidOut {
            Canvas(Modifier.size(50.dp)) {
                drawRect(color = red)
            }
        }
        val quads = Painter().paint(root).filterIsInstance<UiDrawPrimitive.Quad>()

        assertEquals(1, quads.size)
        assertEquals(red, quads[0].color)
        assertEquals(50f, quads[0].w, "the lambda's default size is the node's own")
    }

    @Test
    fun canvasCoordinatesAreNodeLocal() {
        // The whole point of the escape hatch: draw at 0,0 and the walk places it. A gizmo that had
        // to know its own offset would break the moment its container moved.
        val root = laidOut {
            Spacer(Modifier.size(20.dp))
            Canvas(Modifier.size(30.dp)) { drawRect(color = red) }
        }
        val quad = Painter().paint(root).filterIsInstance<UiDrawPrimitive.Quad>().single()

        assertEquals(20f, quad.y, "placed below the spacer, though it drew at 0")
    }

    @Test
    fun canvasIsOneNodeWhateverItDraws() {
        // Why a second immediate-mode engine is not needed: a full-screen overlay is one node.
        val root = laidOut {
            Canvas(Modifier.width(300.dp).height(300.dp)) {
                repeat(50) { i -> drawRect(x = i.toFloat(), y = 0f, width = 1f, height = 10f, color = red) }
            }
        }

        assertEquals(1, root.children.size)
        assertEquals(50, Painter().paint(root).filterIsInstance<UiDrawPrimitive.Quad>().size)
    }

    @Test
    fun onPlacedReportsTreeSpaceBounds() {
        var seen = intArrayOf(-1, -1, -1, -1)
        laidOut {
            Column(Modifier.padding(12.dp)) {
                Spacer(Modifier.size(25.dp).onPlaced { x, y, w, h -> seen = intArrayOf(x, y, w, h) })
            }
        }

        assertEquals(listOf(12, 12, 25, 25), seen.toList())
    }

    @Test
    fun onPlacedFiresAgainAfterARelayout() {
        // Fires every pass rather than on change: a caller that wants change-detection can compare,
        // and a modifier that silently skipped a call is the harder bug to find.
        var calls = 0
        val root = LayoutNode(ColumnMeasurePolicy())
        val content: context(Composer)
        () -> Unit = {
            Spacer(Modifier.size(10.dp).onPlaced { _, _, _, _ -> calls++ })
        }
        composeInto(root, content)
        root.layoutTree(Constraints.of(0, 300, 0, 300))
        root.layoutTree(Constraints.of(0, 300, 0, 300))

        assertTrue(calls >= 2, "was $calls")
    }
}
