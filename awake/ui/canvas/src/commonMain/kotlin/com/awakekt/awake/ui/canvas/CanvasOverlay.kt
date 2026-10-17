/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.gestures.onWheel
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.layout.BoxWithConstraints
import com.awakekt.awake.compose.foundation.layout.FlexBox
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.flex
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
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.graphics.CircleShape
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.graphics.drawImageFill
import com.awakekt.awake.compose.ui.layout.onPlaced
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.drawPath
import com.awakekt.awake.core.input.InputDevice
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.theme.TextStyle

context(_: Composer)
/**
 * Draws [items] over whatever is beneath, lowest [CanvasElement.order] first, and items of the same
 * order as [items] lists them: of two that overlap, the later is on top and takes the pointer. Each
 * item is tagged `canvas-element-<id>` for tests and editor picking. [CanvasElement.touchOnly] elements are drawn
 * only when [showTouchControls] is true.
 *
 * An item whose [CanvasItem.parent] is in [items] is drawn inside that parent's box: anchored and
 * offset within it, after the parent's own content, and hidden when it is. A window and its
 * controls move as one.
 *
 * @param items The elements to draw, each with the nearest canvas element above it the host found,
 *   as its own object graph has it.
 * @param modifier Layout modifier applied to the overlay container.
 * @param showTouchControls Whether touch-only elements should be displayed.
 * @param images The decoded images elements name, by path. An element whose image is missing draws
 *   without it.
 * @param scale Multiplies every element's size, offset, text and image pixels: 2 draws 1x art at
 *   twice its size. Edges land on whole pixels at any scale, so pixel art stays crisp. A scale that
 *   is not above 0 draws at 1.
 * @param resolver Where a [CanvasElement.follow]ed node lands in the frame, a View's camera picture,
 *   and a [CanvasElement.bind]'s field text. Without it, none of those three draw.
 * @param fonts The loaded font files elements name in [CanvasElement.font], by path. An element
 *   whose file is missing draws in the default font.
 * @param strings The text a [CanvasElement.textKey] names, in the active locale; the frame after
 *   [Strings.setLocale] switches, keyed elements show the new locale's text. Without it, a keyed
 *   element shows its key.
 * @param safeArea What covers the edges of the canvas's box, in dp at the host's density: a notch,
 *   rounded corners, a home indicator, overscan. An anchored element on the canvas itself sits
 *   inside it, its anchor and offsets measured from the safe area's edges and centre, unless its
 *   [CanvasElement.safeArea] is false. An element inside another, and one that follows a node, is
 *   placed as before.
 * @param scaler Works the scale out from the box's size each frame, in place of [scale]: by a
 *   reference resolution, a constant pixel size or a constant physical size, within limits and
 *   times a player's multiplier. Null draws at [scale].
 * @param followLimits The most followers of each [CanvasFollowRules.group] drawn at once, keeping
 *   the most important; a group with no limit draws every follower its rules leave in.
 * @param dpi The screen's real dots per inch, for a [scaler] in [CanvasScaleMode.ConstantPhysicalSize];
 *   0, when the host cannot tell, takes 160 per dp, the density's own definition.
 * @param controls What the canvas's on-screen controls share: when touch-only elements show, by
 *   [CanvasControls.touchVisibility] from [showTouchControls] and [inputDevice], and the smallest a
 *   control is to a thumb. Null shows them as [showTouchControls] says.
 * @param inputDevice The device the player last used, for [CanvasTouchVisibility.Auto]: touch
 *   controls show after a touch and hide after a controller, a key or the mouse. Null until any is used.
 */
fun CanvasOverlay(
    items: List<CanvasItem>,
    modifier: Modifier = Modifier,
    showTouchControls: Boolean = false,
    images: Map<String, ImageBitmap> = emptyMap(),
    scale: Float = 1f,
    resolver: CanvasResolver = CanvasResolver.None,
    fonts: Map<String, UiFont> = emptyMap(),
    strings: Strings? = null,
    safeArea: CanvasInsets = CanvasInsets.None,
    scaler: CanvasScaler? = null,
    followLimits: List<CanvasFollowLimit> = emptyList(),
    dpi: Float = 0f,
    controls: CanvasControls? = null,
    inputDevice: InputDevice? = null,
) {
    val origin = remember { CanvasOrigin() }
    val settings = controls ?: CanvasControls.Default
    val touch = settings.touchVisibility.shows(showTouchControls, inputDevice)
    val tree = CanvasTree(items, touch, images, resolver, origin, fonts, strings, settings)
    // Measured before its content is composed, so the scale fits the box, and followers are culled
    // and pinned against it, from the first frame.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth / density else 0f
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight / density else 0f
        val drawnScale = scaler?.scaleFor(width, height, density, if (dpi > 0f) dpi else density * CanvasScaler.DP_PER_INCH) ?: scale
        ScaledCanvas(tree, origin, drawnScale, safeArea, width, height, followLimits)
    }
}

/**
 * [tree]'s elements in a box filling this one, [width] by [height] host dp, drawn at [scale], with
 * anchored roots kept inside [safeArea] and followers thinned by [limits].
 */
context(_: Composer)
private fun ScaledCanvas(
    tree: CanvasTree,
    origin: CanvasOrigin,
    scale: Float,
    safeArea: CanvasInsets,
    width: Float,
    height: Float,
    limits: List<CanvasFollowLimit>,
) {
    val uiScale = if (scale > 0f && scale.isFinite()) scale else 1f
    // The box the elements are placed in, inside any padding the host's modifier adds: where it
    // sits in the frame is what a follower's projected point is moved by.
    Box(Modifier.fillMaxSize().onPlaced { x, y, _, _ -> origin.place(x, y) }) {
        val screen = this
        // A denser dp scales every size, offset, font and image corner beneath it in one place. The
        // host's text style is not the scene's: an element's text looks only as its own data says.
        val canvasDensity = LocalDensity.current * uiScale
        CompositionLocalProvider(
            LocalDensity provides canvasDensity,
            LocalCanvasDensity provides canvasDensity,
            LocalTextStyle provides TextStyle.Default,
        ) {
            // Every follower at once, before any is composed: a limit ranks them all.
            tree.planFollowers(width / uiScale, height / uiScale, canvasDensity, limits)
            // The insets are the host's dp; under the scaled density they shrink by the scale.
            val inside = safeArea.scaledBy(1f / uiScale)
            screen.Elements(tree.roots, tree, inside)
            // Over the controls, so a drag's aim and its cancel zone are never hidden under a button.
            screen.AimGuides(tree, inside)
            // Drawn last, so a dragged element's ghost sits above everything it is dragged over.
            Ghosts(tree)
        }
    }
}

/**
 * Where a canvas's box sat in the frame, in pixels from its top-left, when it was last placed. A
 * follower is placed in composition, before layout, so it reads where the box was the frame before;
 * an element's [CanvasElement.screenBounds] is measured as it is placed, which is after the box it
 * sits in, so from where the box is now. (0, 0) until then, where a canvas filling the window is
 * from its first frame.
 */
internal class CanvasOrigin {
    var x = 0f
        private set
    var y = 0f
        private set

    fun place(x: Int, y: Int) {
        this.x = x.toFloat()
        this.y = y.toFloat()
    }
}

/** [children] inside [element]: each anchored by hand, laid out in rows or columns by its layout, or fanned out by its cluster. */
context(_: Composer)
internal fun BoxScope.Children(element: CanvasElement, children: List<CanvasItem>, tree: CanvasTree) {
    val cluster = element.cluster
    if (cluster != null) {
        ClusterChildren(element, cluster, children, tree)
        return
    }
    val layout = element.layout
    if (layout == null) {
        Elements(children, tree, owner = element)
        return
    }
    FlexBox(Modifier.fillMaxSize().padding(layout.padding.coerceAtLeast(0f).dp), config = layout.toConfig()) {
        for (item in children) {
            val child = item.element
            if (!tree.shows(child) || (child.followArrow && !element.pinned)) continue
            key(child) {
                val share = child.grow.coerceAtLeast(0f)
                CanvasElementView(
                    child,
                    Modifier.flex { grow(share) }.size(child.width.dp, child.height.dp).testTag("canvas-element-${item.id}"),
                    tree,
                    tree.childrenOf(child),
                )
            }
        }
    }
}

/**
 * Places [entries] in this box, each against its anchor, and their children inside them; an anchored
 * entry whose [CanvasElement.safeArea] is true is placed inside [safeArea]. [owner] is the element
 * whose box this is, for a [CanvasElement.followArrow] among the entries.
 */
context(_: Composer)
private fun BoxScope.Elements(
    entries: List<CanvasItem>,
    tree: CanvasTree,
    safeArea: CanvasInsets = CanvasInsets.None,
    owner: CanvasElement? = null,
) {
    for (item in entries) {
        val element = item.element
        val tag = "canvas-element-${item.id}"
        when {
            !tree.shows(element) -> Unit
            // An arrow is drawn only while what it sits in is held to the edge.
            element.followArrow && owner != null -> if (owner.pinned) key(element) { PinArrow(element, owner, tag, tree) }
            element.follow.isNotEmpty() -> tree.followerPlans[element]?.let { plan -> key(element) { Follower(element, plan, tag, tree) } }
            else -> key(element) {
                // The inset wraps the element rather than sitting in its modifier chain, so the
                // element's own bounds (hit area, editor picking) are exactly its size.
                val inside = if (element.safeArea) safeArea else CanvasInsets.None
                Box(Modifier.align(element.anchor.alignment).anchorInset(element.anchor, element.offsetX, element.offsetY, inside)) {
                    CanvasElementView(element, Modifier.size(element.width.dp, element.height.dp).testTag(tag), tree, tree.childrenOf(element))
                }
            }
        }
    }
}

context(_: Composer)
internal fun CanvasElementView(
    element: CanvasElement,
    modifier: Modifier,
    tree: CanvasTree,
    children: List<CanvasItem>,
) {
    val disabled = element.readButtonState(tree)
    val fill = colorOf(element.color, Color.White)
    val back = colorOf(element.background, Color.Transparent)
    val state = rememberStyleState(element.interactions)
    val style = Style {
        background(back)
        element.style.applyTo(this, tree.images, disabled)
    }
    val textStyle = element.textStyle(resolveTextColor(state, style) ?: fill, tree.fontOf(element))
    if (LocalCanvasGhost.current) {
        GhostElementView(element, modifier.rotated(element.drawnRotation).styleable(state, style), textStyle, tree, children)
        return
    }
    // Tree-space pixels back to canvas units: from the canvas box's corner, which its own onPlaced
    // has already reported this pass, and by the density every size and offset dp was resolved
    // against, with the canvas's own scale already folded in by its CompositionLocalProvider.
    val density = LocalDensity.current
    // A follower scaled by distance lays out at a denser dp, but its box is still reported in the
    // canvas's own units.
    val units = LocalCanvasDensity.current.takeIf { it > 0f } ?: density
    val origin = tree.origin
    val placed = modifier.onPlaced { x, y, w, h ->
        val bounds = Rectangle((x - origin.x) / units, (y - origin.y) / units, w / units, h / units)
        element.screenBounds = bounds
        element.placedBounds = bounds
    }
    // Applied before styling and per-kind modifiers, so it covers every kind, Joystick included,
    // and sits outermost -- an ancestor's wheel element only sees what this one left unconsumed.
    val wheeled = if (element.wheel) placed.onWheel(element::addWheel) else placed
    // A Button, or an Image or Panel made pressable, occludes what is under it, like any click
    // target; every other kind only watches, so it must not swallow a tap meant for something it
    // happens to be drawn over.
    val hovered = wheeled.rotated(element.drawnRotation).styleable(state, style)
        .hoverable(element.interactions, blocksHitTest = element.takesTaps)
        .pressPoint(element, density)
    // Ahead of a Button's own `clickable` below, so a real drag's release never also reaches it;
    // a press that stays under the threshold leaves the release for `clickable` to turn into a click.
    val styled = element.draggedModifier(hovered, tree, density)
    when (element.kind) {
        CanvasElementKind.Text -> Box(styled, contentAlignment = (element.textAlign ?: CanvasAnchor.TopLeft).alignment) {
            CanvasText(element, tree.textOf(element), textStyle)
            Children(element, children, tree)
        }
        CanvasElementKind.Panel, CanvasElementKind.Image -> Box(styled.pressableTap(element)) { Children(element, children, tree) }
        CanvasElementKind.Bar -> Box(styled) {
            BarFill(element, tree.valueOf(element), fill, tree.images)
            Children(element, children, tree)
        }
        CanvasElementKind.Button -> ButtonView(element, styled, textStyle, tree, children)
        CanvasElementKind.TextField -> TextFieldView(element, styled, textStyle, tree, children)
        CanvasElementKind.Joystick -> JoystickView(element, wheeled, back, tree, children)
        CanvasElementKind.View -> CameraView(element, styled, tree, children)
        CanvasElementKind.LookArea -> LookAreaView(element, styled, tree, children)
    }
}

/** The canvas's pixels per dp, under the canvas's own scale but not a follower's; 0 outside a canvas. */
internal val LocalCanvasDensity = compositionLocalOf { 0f }

/** How far [CanvasElement.style]'s rotation turns the element, and a pin arrow by its follower's [CanvasElement.pinAngle] too. */
private val CanvasElement.drawnRotation: Float?
    get() = if (followArrow) (style.rotation ?: 0f) + arrowAngle else style.rotation

/**
 * Turns what this node paints -- image, background and content, children included -- clockwise by
 * [degrees] about its own centre, through a draw-only `graphicsLayer`: it never moves the layout
 * box, so hit-testing (and an ancestor's own layout) stays on the unrotated box. Null or zero
 * degrees draws unrotated, with no layer at all.
 */
private fun Modifier.rotated(degrees: Float?): Modifier =
    if (degrees == null || degrees == 0f) this else graphicsLayer(rotationDegrees = degrees)

/**
 * A Bar's fill, [value] of its width: [color], or its fill image. The image is laid out at the bar's
 * full width and cut at the value, so a gauge's end and pattern stay where they are, or with
 * [CanvasFillImageMode.Squeeze] drawn at the filled width, keeping both end caps, or with
 * [CanvasFillImageMode.Sweep] revealed clockwise from 12 o'clock over the Bar's whole box.
 */
context(_: Composer)
private fun BarFill(element: CanvasElement, value: Float, color: Color, images: Map<String, ImageBitmap>) {
    val picture = element.style.fillImage?.fill(images)
    if (element.style.fillImageMode == CanvasFillImageMode.Sweep) {
        SweepFill(value, color, picture)
        return
    }
    val filled = Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f))
    if (picture == null) {
        Box(filled.background(color, RoundedCornerShape((element.style.cornerRadius ?: 0f).dp)))
    } else if (element.style.fillImageMode == CanvasFillImageMode.Squeeze) {
        Box(filled.drawBehind { drawImageFill(picture) })
    } else {
        Box(filled.drawBehind { clipped { drawImageFill(picture, width = element.width * density) } })
    }
}

/**
 * [value]'s share of a full turn, as a wedge clockwise from 12 o'clock over the Bar's whole box:
 * [color], or [picture] cut to the same wedge. The wedge's radius reaches past the box's farthest
 * corner, so [clipped] -- not the wedge's own shape -- is what keeps it inside the box.
 */
context(_: Composer)
internal fun SweepFill(value: Float, color: Color, picture: ImageFill?) {
    val amount = value.coerceIn(0f, 1f)
    if (amount <= 0f) return
    Box(
        Modifier.fillMaxSize().drawBehind {
            // A full turn needs no wedge: its start and end would be the same point, and asking the
            // arc tessellator to close exactly back onto it leaves a hairline, float-precision gap.
            if (amount >= 1f) {
                if (picture == null) drawRect(color = color) else drawImageFill(picture)
                return@drawBehind
            }
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = kotlin.math.hypot(width.toFloat(), height.toFloat())
            val wedge = drawPath {
                moveTo(centerX, centerY)
                lineTo(centerX, centerY - radius)
                arcTo(centerX - radius, centerY - radius, centerX + radius, centerY + radius, SWEEP_START_DEGREES, amount * 360f)
                close()
            }
            clipped {
                if (picture == null) drawPath(wedge, color) else clippedPath(wedge) { drawImageFill(picture) }
            }
        },
    )
}

/** 12 o'clock, in `arcTo`'s degrees clockwise from the positive X axis. */
private const val SWEEP_START_DEGREES = 270f

/**
 * A round pad whose knob follows the pointer holding it, up to the pad's reach, and what its
 * [CanvasElement.joystick] settings make of that: where the pad sits, and how it fades while idle.
 * The element's box is where a thumb may land; the pad and knob are drawn inside it.
 */
context(_: Composer)
private fun JoystickView(
    element: CanvasElement,
    modifier: Modifier,
    back: Color,
    tree: CanvasTree,
    children: List<CanvasItem>,
) {
    val density = LocalDensity.current
    val settings = element.joystick ?: CanvasJoystick.Default
    val stick = element.stick
    val width = element.width
    val height = element.height
    val reach = settings.radiusIn(width, height)
    val centerX = stick.padCenterX(width, height, settings)
    val centerY = stick.padCenterY(width, height, settings)
    val alpha = stick.alpha(settings)
    Box(modifier.stickInput(element, density, tree.touchMarginPx(element, density)).let { if (alpha < 1f) it.alpha(alpha) else it }) {
        Box(Modifier.offset((centerX - reach).dp, (centerY - reach).dp).size((reach * 2f).dp).clip(CircleShape).background(back))
        val knob = reach * 2f * KNOB_FRACTION
        Box(
            Modifier.offset((centerX + stick.knobX - knob / 2f).dp, (centerY + stick.knobY - knob / 2f).dp)
                .size(knob.dp).clip(CircleShape).background(colorOf(element.color, Color.White)),
        )
        Children(element, children, tree)
    }
}

private const val KNOB_FRACTION = 0.45f

/**
 * Moves an element, or a cancel zone, ([x], [y]) dp from its [anchor] with padding around it, not
 * `offset`, so its own bounds (its hit area, editor picking) stay exactly its size wherever it lands.
 *
 * At an edge the offset moves it inward, and a negative one is held at the edge: padding cannot
 * reach past it. On a centred axis the padding goes on one side twice over, which shifts the
 * centred box by the offset: a positive one right or down, a negative one, padded on the other
 * side, left or up.
 */
internal fun Modifier.anchorInset(anchor: CanvasAnchor, x: Float, y: Float, safeArea: CanvasInsets): Modifier {
    val start = when (anchor.column) { 0 -> x.coerceAtLeast(0f); 1 -> 2 * x.coerceAtLeast(0f); else -> 0f }
    val end = when (anchor.column) { 1 -> 2 * (-x).coerceAtLeast(0f); 2 -> x.coerceAtLeast(0f); else -> 0f }
    val top = when (anchor.row) { 0 -> y.coerceAtLeast(0f); 1 -> 2 * y.coerceAtLeast(0f); else -> 0f }
    val bottom = when (anchor.row) { 1 -> 2 * (-y).coerceAtLeast(0f); 2 -> y.coerceAtLeast(0f); else -> 0f }
    // The safe area pads every side, whatever the anchor: an edge element moves in from the covered
    // edge, and a centred one is centred in what is left, the safe area's own centre.
    return padding(
        start = (start + safeArea.left).dp,
        top = (top + safeArea.top).dp,
        end = (end + safeArea.right).dp,
        bottom = (bottom + safeArea.bottom).dp,
    )
}

/** 0 = left, 1 = centre, 2 = right. */
internal val CanvasAnchor.column: Int get() = ordinal % 3

/** 0 = top, 1 = centre, 2 = bottom. */
internal val CanvasAnchor.row: Int get() = ordinal / 3

internal val CanvasAnchor.alignment: Alignment
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
