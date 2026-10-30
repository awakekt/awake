/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.draw.clipToBounds
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.graphics.BlendMode
import com.awakekt.awake.compose.ui.graphics.CircleShape
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object UnclippedNodeType

private val RED = Color(1f, 0f, 0f, 1f)
private val GREEN = Color(0f, 1f, 0f, 1f)
private val BLUE = Color(0f, 0f, 1f, 1f)

private val boxPolicy = MeasurePolicy { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val w = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
    val h = placeables.maxOfOrNull { it.height } ?: constraints.minHeight
    layout(constraints.constrainWidth(w), constraints.constrainHeight(h)) {
        placeables.forEach { it.placeAt(0, 0) }
    }
}

context(_: Composer)
private fun node(
    modifier: Modifier,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    Layout(UnclippedNodeType, modifier = modifier, measurePolicy = boxPolicy, content = content)
}

class DrawScopeTextureAndUnclippedTest {
    @Test
    fun drawTextureWithRotationAndBlendModePreservesRotatedCullingBounds() {
        val marker = Any()
        val root = LayoutNode(boxPolicy)
        composeInto(root) {
            node(
                Modifier.size(40.dp).clipToBounds().alpha(0.5f).drawBehind {
                    // Unrotated x = 43..47 lies outside [0..40], while rotated 90 deg spans x = 33..57 (inside [0..40]).
                    drawTexture(
                        material = marker,
                        x = 43f,
                        y = 8f,
                        width = 4f,
                        height = 24f,
                        premultiplied = true,
                        rotationDegrees = 90f,
                        blendMode = BlendMode.Plus,
                    )
                    // Far outside even when rotated: must still be culled.
                    drawTexture(
                        material = marker,
                        x = 120f,
                        y = 8f,
                        width = 4f,
                        height = 24f,
                        premultiplied = true,
                        rotationDegrees = 90f,
                        blendMode = BlendMode.Plus,
                    )
                },
            )
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val textures = Painter().paint(root).filterIsInstance<UiDrawPrimitive.Texture>()
        assertEquals(1, textures.size)
        val tex = textures.single()
        assertEquals(marker, tex.material)
        assertEquals(90f, tex.rotationDegrees)
        assertEquals(BlendMode.Plus, tex.blendMode)
        assertEquals(0.5f, tex.alpha)
        assertTrue(tex.premultiplied)
    }

    @Test
    fun unclippedSuspendsNestedClipsAndRestoresClipAndCullingEvenAfterThrow() {
        val root = LayoutNode(boxPolicy)
        composeInto(root) {
            node(Modifier.size(40.dp).clipToBounds()) {
                node(
                    Modifier.size(30.dp).clip(CircleShape).drawBehind {
                        unclipped {
                            drawRect(x = 120f, y = 120f, width = 10f, height = 10f, color = RED)
                        }
                        assertFailsWith<IllegalStateException> {
                            unclipped {
                                error("boom")
                            }
                        }
                        // Culling bound must be restored after unclipped {} exits: (120, 120) is culled again.
                        drawRect(x = 120f, y = 120f, width = 10f, height = 10f, color = BLUE)
                        // Inside clip still emits.
                        drawRect(x = 10f, y = 10f, width = 5f, height = 5f, color = GREEN)
                    },
                )
            }
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val primitives = Painter().paint(root)
        val quads = primitives.filterIsInstance<UiDrawPrimitive.Quad>()
        assertEquals(listOf(RED, GREEN), quads.map { it.color })

        var depth = 0
        var redDepth = -1
        var greenDepth = -1
        for (p in primitives) {
            when (p) {
                is UiDrawPrimitive.ClipPush, is UiDrawPrimitive.ClipPathPush -> depth++
                is UiDrawPrimitive.ClipPop -> depth--
                is UiDrawPrimitive.Quad -> {
                    if (p.color == RED) redDepth = depth
                    if (p.color == GREEN) greenDepth = depth
                }
                else -> Unit
            }
        }
        assertEquals(0, depth, "all clip pushes and pops must balance")
        assertEquals(0, redDepth, "quad inside unclipped {} must run at clip depth 0")
        assertEquals(2, greenDepth, "quad after unclipped {} must run back under both clips")
    }

    @Test
    fun unclippedInsideGraphicsLayerDoesNotTouchOuterClips() {
        val root = LayoutNode(boxPolicy)
        composeInto(root) {
            node(Modifier.size(50.dp).clipToBounds()) {
                node(Modifier.size(40.dp).graphicsLayer(alpha = 0.5f)) {
                    node(
                        Modifier.size(20.dp).clipToBounds().drawBehind {
                            unclipped {
                                drawRect(x = 30f, y = 5f, width = 6f, height = 6f, color = RED)
                            }
                        },
                    )
                }
                // Sibling after the graphicsLayer must still be culled by the outer 50x50 clip!
                node(
                    Modifier.size(50.dp).drawBehind {
                        drawRect(x = 120f, y = 5f, width = 10f, height = 10f, color = BLUE)
                    },
                )
            }
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val out = Painter().paintOutput(root)

        assertEquals(1, out.layers.size)
        val layerQuads = out.layers.single().primitives.filterIsInstance<UiDrawPrimitive.Quad>()
        assertEquals(listOf(RED), layerQuads.map { it.color })
        assertTrue(out.primitives.none { it is UiDrawPrimitive.Quad && it.color == BLUE })
    }
}
