/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.graphics.Painter
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEvent
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventType
import io.github.awakelab.awake.compose.ui.input.pointer.PointerInputDispatcher
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

private val red = Color(1f, 0f, 0f, 1f)

private fun quadsOf(modifier: Modifier): List<UiDrawPrimitive.Quad> {
    val root = LayoutNode(ColumnMeasurePolicy())
    composeInto(root) { Spacer(modifier) }
    root.layoutTree(Constraints.of(0, 200, 0, 200))
    return Painter().paint(root).filterIsInstance<UiDrawPrimitive.Quad>()
}

private fun rectOf(quad: UiDrawPrimitive.Quad) = listOf(quad.x, quad.y, quad.w, quad.h)

/**
 * A draw link paints the box at *its* position in the chain, not the node's outer bounds.
 *
 * Both spellings used to emit an identical 40x40 quad, which made modifier order silently
 * meaningless for painting -- a worse failure than Compose's order being silently meaningful,
 * because there is no wrong result to notice.
 */
class ModifierOrderTest {

    @Test
    fun aBackgroundInsidePaddingPaintsThePaddedBox() {
        val quad = quadsOf(Modifier.size(40.dp).padding(8.dp).background(red)).single()

        assertEquals(listOf(8f, 8f, 24f, 24f), rectOf(quad))
    }

    @Test
    fun aBackgroundOutsidePaddingPaintsTheWholeNode() {
        val quad = quadsOf(Modifier.size(40.dp).background(red).padding(8.dp)).single()

        assertEquals(listOf(0f, 0f, 40f, 40f), rectOf(quad))
    }

    @Test
    fun theTwoOrdersDisagree() {
        // Stated as its own assertion: the pair being equal is the regression, and a test that only
        // checked one spelling would still pass with the bug back.
        val inside = quadsOf(Modifier.size(40.dp).padding(8.dp).background(red)).single()
        val outside = quadsOf(Modifier.size(40.dp).background(red).padding(8.dp)).single()

        assertEquals(false, rectOf(inside) == rectOf(outside), "modifier order made no difference")
    }

    @Test
    fun paddingsAccumulateInwards() {
        val quad = quadsOf(
            Modifier.size(40.dp).padding(4.dp).padding(6.dp).background(red),
        ).single()

        assertEquals(listOf(10f, 10f, 20f, 20f), rectOf(quad), "the second padding was ignored")
    }

    @Test
    fun eachLinkSeesItsOwnBox() {
        val quads = quadsOf(
            Modifier.size(40.dp).background(red).padding(8.dp).background(red),
        )

        assertEquals(listOf(0f, 0f, 40f, 40f), rectOf(quads[0]))
        assertEquals(listOf(8f, 8f, 24f, 24f), rectOf(quads[1]))
    }
}

/**
 * A pointer link is clickable on *its* box, the same rule painting follows.
 *
 * `padding().clickable()` shrinks the hit target; `clickable().padding()` does not. Without this the
 * two spellings behaved identically while their painting did not, which is the worse kind of
 * inconsistency -- half of modifier order meaning something.
 */
class PointerOrderTest {

    private fun clicksAt(modifier: Modifier, x: Int, y: Int): Int {
        var clicks = 0
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) { Spacer(modifier.clickable { clicks++ }) }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val dispatcher = PointerInputDispatcher()
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), x, y)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), x, y)
        return clicks
    }

    @Test
    fun paddingBeforeClickableShrinksTheHitTarget() {
        assertEquals(0, clicksAt(Modifier.size(40.dp).padding(8.dp), 2, 2), "the padding ring was clickable")
        assertEquals(1, clicksAt(Modifier.size(40.dp).padding(8.dp), 20, 20))
    }

    @Test
    fun paddingAfterClickableDoesNot() {
        assertEquals(1, clicksAt(Modifier.size(40.dp), 2, 2))
    }
}
