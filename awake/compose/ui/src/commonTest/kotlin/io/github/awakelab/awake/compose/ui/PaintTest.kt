/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.border
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.draw.clip
import io.github.awakelab.awake.compose.ui.draw.clipToBounds
import io.github.awakelab.awake.compose.ui.draw.graphicsLayer
import io.github.awakelab.awake.compose.ui.graphics.BlendMode
import io.github.awakelab.awake.compose.ui.graphics.CircleShape
import io.github.awakelab.awake.compose.ui.graphics.Painter
import io.github.awakelab.awake.compose.ui.graphics.RectangleShape
import io.github.awakelab.awake.compose.ui.graphics.RenderEffect
import io.github.awakelab.awake.compose.ui.graphics.RoundedCornerShape
import io.github.awakelab.awake.compose.ui.layout.Layer
import io.github.awakelab.awake.compose.ui.layout.LayerKind
import io.github.awakelab.awake.compose.ui.layout.Layout
import io.github.awakelab.awake.compose.ui.layout.MeasurePolicy
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.ColoredTriangleMesh
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.core.graphics2d.bounds
import io.github.awakelab.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object PaintedType

private val red = Color(1f, 0f, 0f, 1f)
private val blue = Color(0f, 0f, 1f, 1f)
private val green = Color(0f, 1f, 0f, 1f)

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
    fun graphicsLayerCapturesContentAndLeavesOneTexturePlaceholder() {
        val root = LayoutNode(passthrough)
        composeInto(root) {
            painted(Modifier.size(20.dp).graphicsLayer(alpha = 0.4f).background(red))
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        val output = Painter().paintOutput(root)

        assertEquals(1, output.layers.size)
        assertEquals(20, output.layers.single().width)
        assertEquals(0.4f, output.layers.single().alpha)
        assertEquals(1, output.layers.single().primitives.filterIsInstance<UiDrawPrimitive.Quad>().size)
        assertEquals(0.4f, output.primitives.filterIsInstance<UiDrawPrimitive.Texture>().single().alpha)
    }

    @Test
    fun graphicsLayerScalesAndTranslatesTheCompositedTexture() {
        val root = LayoutNode(passthrough)
        composeInto(root) {
            painted(
                Modifier.size(20.dp).graphicsLayer(
                    scaleX = 2f,
                    scaleY = 0.5f,
                    translationX = 3f,
                    translationY = 4f,
                ).background(red),
            )
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        val texture = Painter().paintOutput(root).primitives.filterIsInstance<UiDrawPrimitive.Texture>().single()

        assertEquals(-7f, texture.x)
        assertEquals(9f, texture.y)
        assertEquals(40f, texture.w)
        assertEquals(10f, texture.h)
    }

    @Test
    fun graphicsLayerCarriesRotationToTheCompositedTexture() {
        val root = LayoutNode(passthrough)
        composeInto(root) { painted(Modifier.size(20.dp).graphicsLayer(rotationDegrees = 45f).background(red)) }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        assertEquals(45f, Painter().paintOutput(root).primitives.filterIsInstance<UiDrawPrimitive.Texture>().single().rotationDegrees)
    }

    @Test
    fun graphicsLayerCarriesBlendModeToTheCompositedTexture() {
        val root = LayoutNode(passthrough)
        composeInto(root) { painted(Modifier.size(20.dp).graphicsLayer(blendMode = BlendMode.Plus).background(red)) }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        assertEquals(BlendMode.Plus, Painter().paintOutput(root).primitives.filterIsInstance<UiDrawPrimitive.Texture>().single().blendMode)
    }

    @Test
    fun graphicsLayerResolvesBlurRadiiInDevicePixels() {
        val root = LayoutNode(passthrough)
        composeInto(root) {
            painted(Modifier.size(20.dp).graphicsLayer(renderEffect = RenderEffect.blur(3.dp, 5.dp)).background(red))
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        val layer = Painter().paintOutput(root).layers.single()
        assertEquals(3f, layer.blurRadiusX)
        assertEquals(5f, layer.blurRadiusY)
    }

    @Test
    fun graphicsLayerBlurReservesTextureSpaceOutsideTheContentBounds() {
        val root = LayoutNode(passthrough)
        composeInto(root) {
            painted(Modifier.size(20.dp).graphicsLayer(renderEffect = RenderEffect.blur(3.dp, 5.dp)).background(red))
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        val output = Painter().paintOutput(root)
        val layer = output.layers.single()
        val texture = output.primitives.filterIsInstance<UiDrawPrimitive.Texture>().single()
        assertEquals(3, layer.effectInsetX)
        assertEquals(5, layer.effectInsetY)
        assertEquals(-3f, texture.x)
        assertEquals(-5f, texture.y)
        assertEquals(26f, texture.w)
        assertEquals(30f, texture.h)
    }

    @Test
    fun graphicsLayerEmitsElevationShadowBeforeItsTexture() {
        val primitives = paintOf { painted(Modifier.size(20.dp).graphicsLayer(shadowElevation = 4.dp).background(red)) }
        assertTrue(primitives[0] is UiDrawPrimitive.ShadowQuad)
        assertTrue(primitives[1] is UiDrawPrimitive.Texture)
    }

    @Test
    fun graphicsLayerElevationUsesItsRoundedShape() {
        val primitives = paintOf {
            painted(
                Modifier.size(20.dp)
                    .graphicsLayer(shadowElevation = 4.dp, shape = RoundedCornerShape(6.dp))
                    .background(red),
            )
        }

        val shadow = assertIs<UiDrawPrimitive.ShadowQuad>(primitives.first())
        assertEquals(6f, shadow.radius)
    }

    @Test
    fun graphicsLayerElevationUsesAPaddedPathMaskForGenericShapes() {
        val root = LayoutNode(passthrough)
        composeInto(root) {
            painted(
                Modifier.size(20.dp)
                    .graphicsLayer(shadowElevation = 4.dp, rotationDegrees = 20f, shape = CircleShape)
                    .background(red),
            )
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        val output = Painter().paintOutput(root)

        assertEquals(2, output.layers.size)
        val mask = output.layers.last()
        assertIs<UiDrawPrimitive.FilledPath>(mask.primitives.single())
        assertEquals(4, mask.effectInsetX)
        val textures = output.primitives.filterIsInstance<UiDrawPrimitive.Texture>()
        assertEquals(2, textures.size)
        assertEquals(20f, textures.first().rotationDegrees)
        assertEquals(0, output.primitives.filterIsInstance<UiDrawPrimitive.ShadowQuad>().size)
    }

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
    fun aUniformShapeKeepsTheRoundedQuadFastPath() {
        val primitives = paintOf {
            painted(Modifier.size(20.dp).background(red, RoundedCornerShape(4.dp)))
        }

        assertEquals(1, primitives.filterIsInstance<UiDrawPrimitive.RoundedQuad>().size)
        assertEquals(0, primitives.filterIsInstance<UiDrawPrimitive.FilledPath>().size)
    }

    @Test
    fun independentRoundedCornersUseThePathPipeline() {
        val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
        val primitives = paintOf { painted(Modifier.size(20.dp).background(red, shape)) }

        assertEquals(1, primitives.filterIsInstance<UiDrawPrimitive.FilledPath>().size)
        assertEquals(0, primitives.filterIsInstance<UiDrawPrimitive.RoundedQuad>().size)
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
            painted(Modifier.size(20.dp).clipToBounds()) {
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
    fun aRoundedShapeClipUsesTheExistingPathClipCommands() {
        val primitives = paintOf {
            painted(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))) {
                painted(Modifier.size(5.dp).background(red))
            }
        }

        assertEquals(1, primitives.filterIsInstance<UiDrawPrimitive.ClipPathPush>().size)
        assertEquals(1, primitives.filterIsInstance<UiDrawPrimitive.ClipPop>().size)
    }

    @Test
    fun rectangleShapeClipRetainsTheRectClipFastPath() {
        val primitives = paintOf {
            painted(Modifier.size(20.dp).clip(RectangleShape)) {
                painted(Modifier.size(5.dp).background(red))
            }
        }

        assertEquals(1, primitives.filterIsInstance<UiDrawPrimitive.ClipPush>().size)
        assertEquals(0, primitives.filterIsInstance<UiDrawPrimitive.ClipPathPush>().size)
    }

    @Test
    fun nestedClipsRestoreTheEnclosingScissor() {
        val primitives = paintOf {
            painted(Modifier.size(40.dp).clipToBounds()) {
                painted(Modifier.size(20.dp).clipToBounds())
            }
        }
        val pops = primitives.filterIsInstance<UiDrawPrimitive.ClipPop>()

        assertEquals(2, pops.size)
        assertEquals(40f, pops[0].restoreRect.width)
        assertEquals(40f, pops[0].restoreRect.height)
        assertEquals(-1e9f, pops[1].restoreRect.x)
        assertEquals(-1e9f, pops[1].restoreRect.y)
    }

    @Test
    fun nestedClipUsesTheIntersectionOfBothViewports() {
        val primitives = paintOf {
            painted(Modifier.size(20.dp).clipToBounds()) {
                painted(Modifier.size(40.dp).clipToBounds())
            }
        }

        val clips = primitives.filterIsInstance<UiDrawPrimitive.ClipPush>()
        assertEquals(2, clips.size)
        assertEquals(20f, clips[1].rect.width)
        assertEquals(20f, clips[1].rect.height)
    }

    @Test
    fun aBorderDrawsOneRingInsetByHalfItsStrokeWidth() {
        val primitives = paintOf { painted(Modifier.size(20.dp).border(2.dp, red)) }
        val bounds = primitives.filterIsInstance<UiDrawPrimitive.Mesh>().single().placedMesh().bounds()

        // Inset, not centred on the edge: the ring's *centreline* sits at (1, 1, 18, 18), half a
        // stroke width in from each side, so the solid part of the border lands inside the 20x20
        // box rather than overflowing the space the parent measured.
        //
        // The box below is that centreline grown by half the stroke width to the ring's outer edge
        // and again by the anti-aliased fringe -- min(AA_FRINGE_PX, stroke / 2) = 1 here. That
        // fringe does extend one pixel beyond the node, which is what an anti-aliased edge is; it
        // is transparent at its outer rim. Asserting this box also pins the stroke width, which a
        // tessellated mesh no longer carries as a field.
        assertEquals(Rectangle(-1f, -1f, 22f, 22f), bounds)

        // The half of that claim a caller actually depends on: everything the border *paints* is
        // inside the node. Only the fringe's outer rim, which is fully transparent, is not.
        val ring = primitives.filterIsInstance<UiDrawPrimitive.Mesh>().single().placedMesh()
        val painted = ColoredTriangleMesh(ring.vertices.filter { it.color.a > 0f }, ring.indices)
        assertEquals(Rectangle(0f, 0f, 20f, 20f), painted.bounds())
    }

    @Test
    fun aPerCornerBorderUsesOneInsetRing() {
        val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
        val primitives = paintOf { painted(Modifier.size(20.dp).border(2.dp, red, shape)) }
        val bounds = primitives.filterIsInstance<UiDrawPrimitive.Mesh>().single().placedMesh().bounds()

        // One ring for the whole perimeter, per-corner radii and all -- same inset and fringe as
        // the square case above.
        assertEquals(Rectangle(-1f, -1f, 22f, 22f), bounds)
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
    fun aDeepLayerPaintsAboveLaterBaseSiblings() {
        val primitives = paintOf {
            painted(Modifier.size(40.dp).background(red)) {
                Layer(LayerKind.Popup, modifier = Modifier.size(8.dp).background(blue), measurePolicy = passthrough)
            }
            painted(Modifier.size(40.dp).background(green))
        }
        val colors = primitives.filterIsInstance<UiDrawPrimitive.Quad>().map { it.color }

        assertEquals(listOf(red, green, blue), colors, "the later sibling covered the deep popup")
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
