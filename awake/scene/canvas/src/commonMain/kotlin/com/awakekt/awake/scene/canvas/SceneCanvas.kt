/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.gestures.draggable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.rememberStyleState
import com.awakekt.awake.compose.foundation.style.resolveTextColor
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.CircleShape
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.graphics.drawImageFill
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform

context(_: Composer)
/**
 * Draws [world]'s visible [CanvasElement]s over whatever is beneath, lowest [CanvasElement.order]
 * first. Each element is tagged `canvas-element-<entity id>` for tests and editor picking.
 * [CanvasElement.touchOnly] elements are drawn only when [showTouchControls] is true.
 *
 * An element whose entity has another element above it in the scene hierarchy is drawn inside the
 * nearest one: anchored and offset within its parent's box, after the parent's own content, and
 * hidden when it is. A window and its controls move as one.
 *
 * @param world The ECS world containing canvas entities to render.
 * @param modifier Layout modifier applied to the overlay container.
 * @param showTouchControls Whether touch-only elements should be displayed.
 * @param images The decoded images elements name, by path, as [loadCanvasImages] reads them. An
 *   element whose image is missing draws without it.
 * @param scale Multiplies every element's size, offset, text and image pixels: 2 draws 1x art at
 *   twice its size. Edges land on whole pixels at any scale, so pixel art stays crisp. A scale that
 *   is not above 0 draws at 1.
 * @param projector Where the scene's nodes land on screen, for elements that [CanvasElement.follow]
 *   one. Without it they are not drawn.
 */
@Suppress("LongParameterList")
fun SceneCanvas(
    world: World,
    modifier: Modifier = Modifier,
    showTouchControls: Boolean = false,
    images: Map<String, ImageBitmap> = emptyMap(),
    scale: Float = 1f,
    projector: CanvasProjector? = null,
) {
    val tree = CanvasTree(world, showTouchControls, images, projector)
    val uiScale = if (scale > 0f && scale.isFinite()) scale else 1f
    Box(modifier.fillMaxSize()) {
        val screen = this
        // A denser dp scales every size, offset, font and image corner beneath it in one place. The
        // host's text style is not the scene's: an element's text looks only as its own data says.
        CompositionLocalProvider(LocalDensity provides LocalDensity.current * uiScale, LocalTextStyle provides TextStyle.Default) {
            screen.Elements(tree.roots, tree)
        }
    }
}

/**
 * The elements of a world, each under the nearest element above it in the scene hierarchy. One that
 * follows a node is placed against the screen wherever it sits in the scene.
 */
private class CanvasTree(
    val world: World,
    private val showTouchControls: Boolean,
    val images: Map<String, ImageBitmap>,
    val projector: CanvasProjector?,
) {
    val roots = ArrayList<Pair<Entity, CanvasElement>>()
    private val children = HashMap<Entity, ArrayList<Pair<Entity, CanvasElement>>>()
    private val named: Map<String, Entity> by lazy {
        HashMap<String, Entity>().also { names -> world.family<Name>().forEach { entity, name -> names.getOrPut(name.value) { entity } } }
    }

    init {
        world.family<CanvasElement>().forEach { entity, element ->
            val parent = if (element.follow.isEmpty()) world.canvasParent(entity) else null
            (if (parent == null) roots else children.getOrPut(parent) { ArrayList() }) += entity to element
        }
        roots.sortBy { it.second.order }
        for (siblings in children.values) siblings.sortBy { it.second.order }
    }

    fun childrenOf(entity: Entity): List<Pair<Entity, CanvasElement>> = children[entity].orEmpty()

    fun shows(element: CanvasElement): Boolean = element.visible && (showTouchControls || !element.touchOnly)

    /** The first node named [name], or null when there is none. */
    fun node(name: String): Entity? = named[name]
}

/**
 * Where a following [element] goes, in dp at [density] pixels each: on its node's screen box, or
 * with its anchor point on the node's screen point, nudged inward by its offsets. Null hides it: no
 * projector, no such node, or the node behind the camera.
 */
private fun CanvasTree.placeFollower(element: CanvasElement, density: Float): Rectangle? {
    val projector = projector
    val node = node(element.follow)
    if (projector == null || node == null) return null
    val box = if (element.followBounds) projector.bounds(node) else null
    return if (box != null) {
        Rectangle(box.x / density, box.y / density, box.width / density, box.height / density)
    } else {
        placeOnPoint(element, node, projector, density)
    }
}

/** [element] with its anchor point on [node]'s screen point, or null when the node is behind the camera. */
private fun CanvasTree.placeOnPoint(element: CanvasElement, node: Entity, projector: CanvasProjector, density: Float): Rectangle? {
    val offset = element.followOffset
    val point = world.get<Transform>(node)?.worldMatrix?.let { projector.project(it.m03 + offset.x, it.m13 + offset.y, it.m23 + offset.z) }
        ?: return null
    val column = element.anchor.column
    val row = element.anchor.row
    val x = point.x / density - column / 2f * element.width + if (column == 2) -element.offsetX else element.offsetX
    val y = point.y / density - row / 2f * element.height + if (row == 2) -element.offsetY else element.offsetY
    return Rectangle(x, y, element.width, element.height)
}

private fun World.canvasParent(entity: Entity): Entity? {
    var node = get<Transform>(entity)?.parent
    while (node != null) {
        if (has(node, CanvasElement::class)) return node
        node = get<Transform>(node)?.parent
    }
    return null
}

/** Places [entries] in this box, each against its anchor, and their children inside them. */
context(_: Composer)
private fun BoxScope.Elements(entries: List<Pair<Entity, CanvasElement>>, tree: CanvasTree) {
    for ((entity, element) in entries) {
        if (!tree.shows(element)) continue
        key(entity) {
            val tag = "canvas-element-${entity.id}"
            if (element.follow.isEmpty()) {
                // The inset wraps the element rather than sitting in its modifier chain, so the
                // element's own bounds (hit area, editor picking) are exactly its size.
                Box(Modifier.align(element.anchor.alignment).anchorInset(element)) {
                    CanvasElementView(element, Modifier.size(element.width.dp, element.height.dp).testTag(tag), tree, tree.childrenOf(entity))
                }
            } else {
                val placed = tree.placeFollower(element, LocalDensity.current)
                if (placed != null) {
                    Box(Modifier.offset(placed.x.dp, placed.y.dp)) {
                        CanvasElementView(element, Modifier.size(placed.width.dp, placed.height.dp).testTag(tag), tree, tree.childrenOf(entity))
                    }
                }
            }
        }
    }
}

context(_: Composer)
private fun CanvasElementView(
    element: CanvasElement,
    modifier: Modifier,
    tree: CanvasTree,
    children: List<Pair<Entity, CanvasElement>>,
) {
    val fill = colorOf(element.color, Color.White)
    val back = colorOf(element.background, Color.Transparent)
    val state = rememberStyleState(element.interactions)
    val style = Style {
        background(back)
        element.style.applyTo(this, tree.images)
    }
    val textStyle = TextStyle(
        color = resolveTextColor(state, style) ?: fill,
        size = element.fontSize.sp,
        shadow = element.style.textShadow?.toTextShadow(),
        outline = element.style.textOutline?.toTextOutline(),
    )
    val styled = modifier.styleable(state, style)
    when (element.kind) {
        CanvasElementKind.Text -> Box(styled, contentAlignment = (element.textAlign ?: CanvasAnchor.TopLeft).alignment) {
            Text(element.text, style = textStyle)
            Elements(children, tree)
        }
        CanvasElementKind.Panel, CanvasElementKind.Image -> Box(styled) { Elements(children, tree) }
        CanvasElementKind.Bar -> Box(styled) {
            BarFill(element, fill, tree.images)
            Elements(children, tree)
        }
        CanvasElementKind.Button -> Box(
            styled.hoverable(element.interactions).clickable(element.interactions) { element.press() },
            contentAlignment = (element.textAlign ?: CanvasAnchor.Center).alignment,
        ) {
            Text(element.text, style = textStyle)
            Elements(children, tree)
        }
        CanvasElementKind.Joystick -> JoystickView(element, modifier, back, tree, children)
    }
}

/**
 * A Bar's fill, [CanvasElement.value] of its width: [color], or its fill image laid out at the
 * bar's full width and cut at the value, so a gauge's end and pattern stay where they are.
 */
context(_: Composer)
private fun BarFill(element: CanvasElement, color: Color, images: Map<String, ImageBitmap>) {
    val filled = Modifier.fillMaxHeight().fillMaxWidth(element.value.coerceIn(0f, 1f))
    val picture = element.style.fillImage?.fill(images)
    if (picture == null) {
        Box(filled.background(color, RoundedCornerShape((element.style.cornerRadius ?: 0f).dp)))
    } else {
        Box(filled.drawBehind { clipped { drawImageFill(picture, width = element.width * density) } })
    }
}

/** A round pad whose knob follows a drag, up to the pad's edge, and springs back on release. */
context(_: Composer)
private fun JoystickView(
    element: CanvasElement,
    modifier: Modifier,
    back: Color,
    tree: CanvasTree,
    children: List<Pair<Entity, CanvasElement>>,
) {
    val density = LocalDensity.current
    val radius = minOf(element.width, element.height) / 2f * density
    Box(
        modifier.clip(CircleShape).background(back)
            .draggable(
                onDrag = { dx, dy -> element.dragStick(dx.toFloat(), dy.toFloat(), radius) },
                onDragStopped = element::releaseStick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val size = minOf(element.width, element.height) * KNOB_FRACTION
        Box(
            Modifier.offset((element.knobX / density).dp, (element.knobY / density).dp)
                .size(size.dp).clip(CircleShape).background(colorOf(element.color, Color.White)),
        )
        Elements(children, tree)
    }
}

private const val KNOB_FRACTION = 0.45f

/**
 * Moves the element inward from its anchor with padding, not `offset`: `offset` only moves the
 * drawing, so hit-testing and editor picking would still find the element at the anchor.
 * On a centred axis the padding goes on one side twice over, which shifts the centred box by the
 * offset. Offsets pointing off-screen (negative) are clamped to the anchor.
 */
private fun Modifier.anchorInset(element: CanvasElement): Modifier {
    val x = element.offsetX.coerceAtLeast(0f)
    val y = element.offsetY.coerceAtLeast(0f)
    val start = when (element.anchor.column) { 0 -> x; 1 -> 2 * x; else -> 0f }
    val end = if (element.anchor.column == 2) x else 0f
    val top = when (element.anchor.row) { 0 -> y; 1 -> 2 * y; else -> 0f }
    val bottom = if (element.anchor.row == 2) y else 0f
    return padding(start = start.dp, top = top.dp, end = end.dp, bottom = bottom.dp)
}

/** 0 = left, 1 = centre, 2 = right. */
private val CanvasAnchor.column: Int get() = ordinal % 3

/** 0 = top, 1 = centre, 2 = bottom. */
private val CanvasAnchor.row: Int get() = ordinal / 3

private val CanvasAnchor.alignment: Alignment
    get() = when (this) {
        CanvasAnchor.TopLeft -> Alignment.TopStart
        CanvasAnchor.TopCenter -> Alignment.TopCenter
        CanvasAnchor.TopRight -> Alignment.TopEnd
        CanvasAnchor.CenterLeft -> Alignment.CenterStart
        CanvasAnchor.Center -> Alignment.Center
        CanvasAnchor.CenterRight -> Alignment.CenterEnd
        CanvasAnchor.BottomLeft -> Alignment.BottomStart
        CanvasAnchor.BottomCenter -> Alignment.BottomCenter
        CanvasAnchor.BottomRight -> Alignment.BottomEnd
    }

// A colour typed wrong in the editor falls back instead of taking the whole frame down.
internal fun colorOf(hex: String?, fallback: Color): Color =
    if (hex != null && isHexColor(hex)) Color.fromHex(hex) else fallback
