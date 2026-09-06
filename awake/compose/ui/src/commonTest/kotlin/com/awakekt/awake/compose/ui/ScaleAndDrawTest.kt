/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.draw.drawWithContent
import com.awakekt.awake.compose.ui.draw.scale
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object ScaledType

private val stack = MeasurePolicy { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val w = placeables.maxOfOrNull { it.width } ?: 0
    val h = placeables.maxOfOrNull { it.height } ?: 0
    layout(constraints.constrainWidth(w), constraints.constrainHeight(h)) {
        placeables.forEach { it.placeAt(0, 0) }
    }
}

private val red = Color(1f, 0f, 0f, 1f)

context(_: Composer)
private fun node(
    modifier: Modifier,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) =
    Layout(ScaledType, modifier = modifier, measurePolicy = stack, content = content)

private fun painted(content: context(Composer) () -> Unit): List<UiDrawPrimitive> {
    val root = LayoutNode(stack)
    composeInto(root, content)
    root.layoutTree(Constraints.of(0, 200, 0, 200))
    return Painter().paint(root)
}

private fun quads(primitives: List<UiDrawPrimitive>) =
    primitives.filterIsInstance<UiDrawPrimitive.Quad>()

class ScaleTest {

    @Test
    fun itScalesAboutTheNodesOwnCentre() {
        // 40x40 at the origin, doubled about its centre (20,20): corners move to -20..60.
        val quad = quads(painted { node(Modifier.size(40.dp).scale(2f).background(red)) }).first()

        assertEquals(-20f, quad.x)
        assertEquals(-20f, quad.y)
        assertEquals(80f, quad.w)
        assertEquals(80f, quad.h)
    }

    @Test
    fun theAxesAreIndependent() {
        val quad = quads(painted { node(Modifier.size(40.dp).scale(2f, 1f).background(red)) }).first()

        assertEquals(80f, quad.w)
        assertEquals(40f, quad.h, "the y axis was scaled too")
    }

    @Test
    fun nestingComposesMultiplicatively() {
        // The divergence `ui-core` documents about its own transform stack: nested scale blocks do
        // NOT compose there. Here 2x inside 2x is 4x, and this test is why that claim is checkable.
        val quad = quads(
            painted {
                node(Modifier.scale(2f)) {
                    node(Modifier.size(10.dp).scale(2f).background(red))
                }
            },
        ).first()

        assertEquals(40f, quad.w, "2x inside 2x should be 4x on a 10dp box")
    }

    @Test
    fun aScaleAndItsInverseCancel() {
        // The case a scale+pivot representation cannot express: solving for a combined pivot divides
        // by `1 - s1*s2`, which is exactly zero here. The flat affine has no such hole.
        val plain = quads(painted { node(Modifier.size(40.dp).background(red)) }).first()
        val cancelled = quads(
            painted {
                node(Modifier.scale(2f)) {
                    node(Modifier.size(40.dp).scale(0.5f).background(red))
                }
            },
        ).first()

        assertEquals(plain.w, cancelled.w)
        assertEquals(plain.x, cancelled.x)
    }

    @Test
    fun aScaleAfterTheThingItWouldScaleDoesNothing() {
        // Chain position is meaningful, exactly as it is for alpha: a link scales what comes after
        // it, so `background(red).scale(2f)` leaves the background alone. Pinned rather than left to
        // be rediscovered -- it is the direction that surprises people.
        val quad = quads(painted { node(Modifier.size(40.dp).background(red).scale(2f)) }).first()

        assertEquals(40f, quad.w)
    }

    @Test
    fun anIdentityScaleAddsNoLink() {
        // Same guard `alpha(1f)` has: a no-op link still costs a chain entry and a draw call every
        // frame, on a path that runs 60 times a second.
        assertEquals(Modifier, Modifier.scale(1f))
    }

    @Test
    fun itScalesEveryPrimitiveKindItWraps() {
        // Why this is a CPU-side affine rather than the GPU's DrawTransform: only four of the nine
        // primitive kinds carry that field, so a rounded quad under a scaled subtree would silently
        // stay put.
        val primitives = painted {
            node(Modifier.size(40.dp).scale(2f).background(red, cornerRadius = 8.dp))
        }
        val rounded = primitives.filterIsInstance<UiDrawPrimitive.RoundedQuad>().first()

        assertEquals(80f, rounded.w)
        assertEquals(16f, rounded.radius, "the corner radius did not scale with the box")
    }
}

class DrawWithContentTest {

    @Test
    fun contentCanBePaintedBeforeOrAfterTheModifiersOwnDrawing() {
        val order = mutableListOf<String>()
        painted {
            node(
                Modifier.size(20.dp).drawWithContent {
                    order += "under"
                    drawContent()
                    order += "over"
                },
            )
        }

        assertEquals(listOf("under", "over"), order)
    }

    @Test
    fun itCanDrawOnBothSidesOfTheContent() {
        // The whole reason this exists next to drawBehind: a scrim needs paint under *and* over.
        val primitives = painted {
            node(
                Modifier.size(20.dp).drawWithContent {
                    drawRect(color = red)
                    drawContent()
                    drawRect(color = red)
                },
            )
        }

        assertEquals(2, quads(primitives).size)
    }

    @Test
    fun skippingDrawContentHidesWhatItWraps() {
        val primitives = painted {
            node(Modifier.drawWithContent { /* content deliberately not drawn */ }) {
                node(Modifier.size(20.dp).background(red))
            }
        }

        assertTrue(quads(primitives).isEmpty(), "the wrapped content painted anyway")
    }
}
