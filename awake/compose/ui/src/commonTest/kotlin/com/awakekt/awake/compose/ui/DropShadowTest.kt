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
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.draw.dropShadow
import com.awakekt.awake.compose.ui.draw.scale
import com.awakekt.awake.compose.ui.graphics.Brush
import com.awakekt.awake.compose.ui.graphics.CircleShape
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.graphics.RectangleShape
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.graphics.shadow.Shadow
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.DpOffset
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DropShadowTest {

    private val shadow = Shadow(
        radius = 3.dp,
        color = Color.Black,
        spread = 2.dp,
        offset = DpOffset(4.dp, 5.dp),
        alpha = 0.6f,
    )

    private fun paint(content: context(Composer) () -> Unit): List<UiDrawPrimitive> {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root, content)
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        return Painter().paint(root)
    }

    @Test
    fun lowLevelDrawShadowIsPlacedAtItsOwnNode() {
        val shadow = paint {
            Column {
                Spacer(Modifier.size(40.dp))
                Spacer(Modifier.size(20.dp).thenShadow())
            }
        }.single() as UiDrawPrimitive.ShadowQuad

        assertTrue(shadow.y >= 40f, "shadow drew at ${shadow.y}, above its own node")
    }

    @Test
    fun modifierResolvesRoundedShapeAndDensityIndependentParameters() {
        val output = paint {
            Spacer(Modifier.size(20.dp).dropShadow(RoundedCornerShape(4.dp), shadow))
        }.single() as UiDrawPrimitive.ShadowQuad

        assertEquals(4f, output.radius)
        assertEquals(4f, output.offsetX)
        assertEquals(5f, output.offsetY)
        assertEquals(3f, output.blurRadius)
        assertEquals(2f, output.spread)
        assertEquals(0.6f, output.color.a)
    }

    @Test
    fun alphaRespectsModifierOrder() {
        val before = paint {
            Spacer(Modifier.size(20.dp).alpha(0.5f).dropShadow(RectangleShape, shadow))
        }.single() as UiDrawPrimitive.ShadowQuad
        val after = paint {
            Spacer(Modifier.size(20.dp).dropShadow(RectangleShape, shadow).alpha(0.5f))
        }.single() as UiDrawPrimitive.ShadowQuad

        assertEquals(0.3f, before.color.a)
        assertEquals(0.6f, after.color.a)
    }

    @Test
    fun scaleReachesShadowGeometryAndEffects() {
        val output = paint {
            Spacer(Modifier.size(20.dp).scale(2f).dropShadow(RectangleShape, shadow))
        }.single() as UiDrawPrimitive.ShadowQuad

        assertEquals(40f, output.w)
        assertEquals(40f, output.h)
        assertEquals(8f, output.offsetX)
        assertEquals(10f, output.offsetY)
        assertEquals(6f, output.blurRadius)
        assertEquals(4f, output.spread)
    }

    @Test
    fun genericShapeUsesAPaddedPathMaskInsteadOfACastingBoundingBox() {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Spacer(Modifier.size(20.dp).dropShadow(CircleShape, shadow.copy(spread = 0.dp)))
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        val output = Painter().paintOutput(root)

        assertEquals(1, output.layers.size)
        assertIs<UiDrawPrimitive.FilledPath>(output.layers.single().primitives.single())
        assertEquals(3, output.layers.single().effectInsetX)
        assertTrue(output.primitives.single() is UiDrawPrimitive.Texture)
    }

    @Test
    fun linearGradientBrushPreservesAllStopsAndShadowAlpha() {
        val output = paint {
            Spacer(
                Modifier.size(20.dp).dropShadow(
                    RectangleShape,
                    Shadow(
                        radius = 2.dp,
                        alpha = 0.5f,
                        brush = Brush.horizontal(Color(1f, 0f, 0f, 1f), Color(0f, 0f, 1f, 0.8f)),
                    ),
                ),
            )
        }.single() as UiDrawPrimitive.ShadowQuad

        val gradient = requireNotNull(output.gradient)
        assertEquals(0.5f, gradient.topLeft.a)
        assertEquals(0.4f, gradient.topRight.a)
        assertEquals(gradient.topLeft, gradient.bottomLeft)
        assertEquals(gradient.topRight, gradient.bottomRight)
    }

    private fun Modifier.thenShadow(): Modifier =
        drawBehind {
            drawShadow(color = Color.Black)
        }
}
