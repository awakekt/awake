// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui

import io.github.ronjunevaldoz.awake.compose.foundation.background
import io.github.ronjunevaldoz.awake.compose.foundation.border
import io.github.ronjunevaldoz.awake.compose.foundation.layout.padding
import io.github.ronjunevaldoz.awake.compose.foundation.layout.size
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.ui.draw.clip
import io.github.ronjunevaldoz.awake.compose.ui.graphics.Painter
import io.github.ronjunevaldoz.awake.compose.ui.layout.Layer
import io.github.ronjunevaldoz.awake.compose.ui.layout.LayerKind
import io.github.ronjunevaldoz.awake.compose.ui.layout.Layout
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy
import io.github.ronjunevaldoz.awake.compose.ui.layout.composeInto
import io.github.ronjunevaldoz.awake.compose.ui.layout.layoutTree
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object PaintedType

private val red = Color(1f, 0f, 0f, 1f)
private val blue = Color(0f, 0f, 1f, 1f)

private val passthrough = MeasurePolicy { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val w = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
    val h = placeables.maxOfOrNull { it.height } ?: constraints.minHeight
    layout(constraints.constrainWidth(w), constraints.constrainHeight(h)) {
        placeables.forEach { it.placeAt(0, 0) }
    }
}

context(_: Composer)
private fun painted(
    modifier: Modifier,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) =
    Layout(PaintedType, modifier = modifier, measurePolicy = passthrough, content = content)

private fun paintOf(
    constraints: Constraints = Constraints.of(0, 200, 0, 200),
    content: context(Composer) () -> Unit,
): List<UiDrawPrimitive> {
    val root = LayoutNode(passthrough)
    composeInto(root, content)
    root.layoutTree(constraints)
    return Painter().paint(root)
}

class PaintTest {

    @Test
    fun aBackgroundBecomesOneQuad() {
        val primitives = paintOf { painted(Modifier.size(20.dp).background(red)) }
        val quads = primitives.filterIsInstance<UiDrawPrimitive.Quad>()

        assertEquals(1, quads.size)
        assertEquals(red, quads[0].color)
        assertEquals(20f, quads[0].w)
    }

    @Test
    fun aZeroRadiusBackgroundIsAPlainQuadNotARoundedOne() {
        // Emitting a RoundedQuad for every square background would make the backend run its
        // rounded path for the overwhelmingly common case.
        val primitives = paintOf { painted(Modifier.size(20.dp).background(red)) }

        assertEquals(1, primitives.filterIsInstance<UiDrawPrimitive.Quad>().size)
        assertEquals(0, primitives.filterIsInstance<UiDrawPrimitive.RoundedQuad>().size)
    }

    @Test
    fun aCornerRadiusProducesARoundedQuad() {
        val primitives = paintOf { painted(Modifier.size(20.dp).background(red, cornerRadius = 4.dp)) }
        val rounded = primitives.filterIsInstance<UiDrawPrimitive.RoundedQuad>()

        assertEquals(1, rounded.size)
        assertEquals(4f, rounded[0].radius)
    }

    @Test
    fun primitivesCarryTreeSpaceCoordinatesNotNodeLocalOnes() {
        // A draw node paints at 0,0 in its own space; the walk adds the origin. Getting this wrong
        // stacks every widget in the top-left corner.
        val primitives = paintOf {
            painted(Modifier.padding(10.dp)) {
                painted(Modifier.size(5.dp).background(red))
            }
        }
        val quad = primitives.filterIsInstance<UiDrawPrimitive.Quad>().single()

        assertEquals(10f, quad.x)
        assertEquals(10f, quad.y)
    }

    @Test
    fun aBackgroundPaintsUnderneathItsChildren() {
        val primitives = paintOf {
            painted(Modifier.size(40.dp).background(red)) {
                painted(Modifier.size(10.dp).background(blue))
            }
        }
        val colors = primitives.filterIsInstance<UiDrawPrimitive.Quad>().map { it.color }

        assertEquals(listOf(red, blue), colors, "the parent's background is emitted first")
    }

    @Test
    fun chainOrderDecidesWhatPaintsOnTop() {
        val first = paintOf { painted(Modifier.size(20.dp).background(red).background(blue)) }
        val colors = first.filterIsInstance<UiDrawPrimitive.Quad>().map { it.color }

        assertEquals(listOf(red, blue), colors, "the outer link paints first, so blue lands on top")
    }

    @Test
    fun clipPushesAndPopsAroundItsContent() {
        val primitives = paintOf {
            painted(Modifier.size(20.dp).clip()) {
                painted(Modifier.size(5.dp).background(red))
            }
        }

        assertEquals(1, primitives.filterIsInstance<UiDrawPrimitive.ClipPush>().size)
        assertEquals(1, primitives.filterIsInstance<UiDrawPrimitive.ClipPop>().size)
        assertTrue(
            primitives.indexOfFirst { it is UiDrawPrimitive.Quad } <
                primitives.indexOfFirst { it is UiDrawPrimitive.ClipPop },
            "content sits between the push and the pop",
        )
    }

    @Test
    fun aBorderDrawsFourEdgesInsideTheBounds() {
        val primitives = paintOf { painted(Modifier.size(20.dp).border(2.dp, red)) }
        val quads = primitives.filterIsInstance<UiDrawPrimitive.Quad>()

        assertEquals(4, quads.size)
        // Inside, not centred on the edge: an outside border would overflow the space the parent
        // measured, so a bordered child would overlap its sibling.
        assertTrue(quads.all { it.x >= 0f && it.y >= 0f })
        assertTrue(quads.all { it.x + it.w <= 20f && it.y + it.h <= 20f })
    }

    @Test
    fun aLayerPaintsAfterTheSubtreeThatDeclaredIt() {
        val primitives = paintOf {
            painted(Modifier.size(40.dp).background(red)) {
                Layer(LayerKind.Popup, modifier = Modifier.size(8.dp).background(blue), measurePolicy = passthrough)
            }
        }
        val colors = primitives.filterIsInstance<UiDrawPrimitive.Quad>().map { it.color }

        assertEquals(listOf(red, blue), colors, "the popup is on top of the content that owns it")
    }

    @Test
    fun layersPaintInKindOrderNotDeclarationOrder() {
        // A tooltip declared before a dialog still lands above it -- ordering is data, not the
        // sequence someone happened to declare in.
        val primitives = paintOf {
            Layer(LayerKind.Tooltip, modifier = Modifier.size(4.dp).background(blue), measurePolicy = passthrough)
            Layer(LayerKind.Popup, modifier = Modifier.size(4.dp).background(red), measurePolicy = passthrough)
        }
        val colors = primitives.filterIsInstance<UiDrawPrimitive.Quad>().map { it.color }

        assertEquals(listOf(red, blue), colors, "Popup ordinal precedes Tooltip")
    }

    @Test
    fun anEmptyTreePaintsNothing() {
        assertEquals(emptyList(), paintOf { })
    }
}
