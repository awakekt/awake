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
import com.awakekt.awake.compose.ui.draw.scale
import com.awakekt.awake.compose.ui.graphics.CircleShape
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.graphics.drawImageFill
import com.awakekt.awake.compose.ui.graphics.drawImageFillBar
import com.awakekt.awake.compose.ui.layout.onPlaced
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalFrameClock
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
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
 * @param slotContent The composables a control's [CanvasSlot.custom] names, by name, each given the
 *   control's live state; a slot naming one that is missing draws its style instead.
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
    slotContent: Map<String, CanvasSlotContent> = emptyMap(),
) {
    val origin = remember { CanvasOrigin() }
    val tooltips = remember { CanvasTooltips() }
    // Kept across frames: each follower's size bucket and motion, and planning's reused lists.
    val followers = remember { FollowerPlans() }
    // Kept across frames: premultiplied copies of the particle sheets in images.
    val premultiplied = remember { PremultipliedImages() }
    val settings = controls ?: CanvasControls.Default
    val touch = settings.touchVisibility.shows(showTouchControls, inputDevice)
    val tree = CanvasTree(items, touch, images, resolver, origin, fonts, strings, settings, tooltips, followers, premultiplied)
    // Measured before its content is composed, so the scale fits the box, and followers are culled
    // and pinned against it, from the first frame.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth / density else 0f
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight / density else 0f
        val effectiveDpi = if (dpi > 0f) dpi else density * CanvasScaler.DP_PER_INCH
        val drawnScale = scaler?.scaleFor(width, height, density, effectiveDpi) ?: scale
        val box = CanvasBoxMetrics(width, height, density, effectiveDpi, safeArea)
        CompositionLocalProvider(LocalCanvasSlotContent provides slotContent) {
            ScaledCanvas(tree, origin, drawnScale, box, followLimits)
        }
    }
}

/**
 * [tree]'s elements in a box filling this one, measured in [box], drawn at [scale], with anchored
 * roots kept inside [CanvasBoxMetrics.safeArea] and followers thinned by [limits].
 */
context(_: Composer)
private fun ScaledCanvas(
    tree: CanvasTree,
    origin: CanvasOrigin,
    scale: Float,
    box: CanvasBoxMetrics,
    limits: List<CanvasFollowLimit>,
) {
    val uiScale = if (scale > 0f && scale.isFinite()) scale else 1f
    val particleClock = remember { CanvasParticlesClock() }
    particleClock.advance(LocalFrameClock.current.deltaSeconds)
    // The box the elements are placed in, inside any padding the host's modifier adds: where it
    // sits in the frame is what a follower's projected point is moved by.
    Box(Modifier.fillMaxSize().onPlaced { x, y, _, _ -> origin.place(x, y) }) {
        val screen = this
        // A denser dp scales every size, offset, font and image corner beneath it in one place. The
        // host's text style is not the scene's: an element's text looks only as its own data says.
        val canvasDensity = box.hostDensity * uiScale
        tree.measureBox(box, canvasDensity)
        CompositionLocalProvider(
            LocalDensity provides canvasDensity,
            LocalCanvasDensity provides canvasDensity,
            LocalCanvasParticlesClock provides particleClock,
            LocalTextStyle provides TextStyle.Default,
        ) {
            // Every follower at once, before any is composed: a limit ranks them all.
            tree.planFollowers(box.width / uiScale, box.height / uiScale, canvasDensity, limits)
            stepUncomposedVisibleFollowers(tree, particleClock)
            // The insets are the host's dp; under the scaled density they shrink by the scale.
            val inside = box.safeArea.scaledBy(1f / uiScale)
            screen.Elements(tree.roots, tree, inside)
            // Over the controls, so a drag's aim and its cancel zone are never hidden under a button.
            screen.AimGuides(tree, inside)
            // Drawn last, so a dragged element's ghost sits above everything it is dragged over.
            Ghosts(tree)
            // Last of all, so nothing it hangs over hides it.
            Tooltip(tree)
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
    if (element.particles?.placement == CanvasParticlePlacement.OverContent) {
        ParticlesOverContent(element, tree)
    }
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
                WithItemScaler(item, tree) {
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
}

/** Runs [content] under [item]'s own subtree [CanvasScaler] density, or unchanged when it has none. */
context(_: Composer)
internal fun WithItemScaler(item: CanvasItem, tree: CanvasTree, content: context(Composer) () -> Unit) {
    val scaler = item.scaler
    if (scaler == null) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides tree.box.densityOf(scaler), content = content)
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
            element.followArrow && owner != null -> if (owner.pinned) key(element) { PinArrow(item, owner, tag, tree) }
            element.follow.isNotEmpty() -> tree.followerPlans[element]?.takeIf { it.drawn }?.let { plan -> key(element) { Follower(element, plan, tag, tree) } }
            else -> key(element) {
                val scaler = item.scaler
                if (scaler == null) {
                    AnchoredElement(element, tag, tree, safeArea)
                } else {
                    val itemScale = tree.box.scaleOf(scaler)
                    val itemSafeArea = if (owner == null) tree.box.safeArea.scaledBy(1f / itemScale) else CanvasInsets.None
                    CompositionLocalProvider(LocalDensity provides tree.box.hostDensity * itemScale) {
                        AnchoredElement(element, tag, tree, itemSafeArea)
                    }
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
    val resolved = rememberCanvasStyleState(element.style, back, fill, state, disabled, tree.controls.theme?.transitionSeconds)
    val behindParticles = element.particles?.placement == CanvasParticlePlacement.BehindContent
    val style = Style { resolved.applyTo(this, element.style, tree.images, applyAlpha = !behindParticles) }
    val textStyle = element.textStyle(resolved.textColor, tree.fontOf(element))
    if (LocalCanvasGhost.current) {
        GhostElementView(element, modifier.rotated(element.drawnRotation).scaled(resolved.scale).styleable(state, style), textStyle, tree, children)
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
    val transformed = wheeled.rotated(element.drawnRotation).scaled(resolved.scale)
    val base = if (behindParticles) {
        val faded = if (resolved.alpha < 1f) transformed.alpha(resolved.alpha.coerceIn(0f, 1f)) else transformed
        faded.canvasParticlesBehind(element, tree)
    } else {
        transformed
    }
    // A Button, or an Image or Panel made pressable, occludes what is under it, like any click
    // target; every other kind only watches, so it must not swallow a tap meant for something it
    // happens to be drawn over.
    val hovered = base.styleable(state, style)
        .hoverable(element.interactions, blocksHitTest = element.takesTaps)
        .pressPoint(element, density, units)
        .tooltipPointer(element, tree.tooltips, units)
    // Ahead of a Button's own `clickable` below, so a real drag's release never also reaches it;
    // a press that stays under the threshold leaves the release for `clickable` to turn into a click.
    val styled = element.movedModifier(element.draggedModifier(hovered, tree, units), tree, units)
    ElementContent(element, styled, if (behindParticles) base else wheeled, back, textStyle, tree, children)
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
 * Scales what this node paints about its own centre without moving its layout box or hit area.
 * Unit scale adds no modifier node.
 */
private fun Modifier.scaled(factor: Float): Modifier = if (factor == 1f) this else scale(factor)

/**
 * A Bar's fill, [value] of its width: [color], or its fill image. The image is laid out at the bar's
 * full width and cut at the value, so a gauge's end and pattern stay where they are, or with
 * [CanvasFillImageMode.Squeeze] drawn at the filled width, keeping both end caps, with
 * [CanvasFillImageMode.Capped] drawn with both caps whole at every value, or with
 * [CanvasFillImageMode.Sweep] revealed clockwise from 12 o'clock over the Bar's whole box.
 */
context(_: Composer)
internal fun BarFill(element: CanvasElement, value: Float, color: Color, images: Map<String, ImageBitmap>) {
    val picture = element.style.fillImage?.fill(images)
    if (element.style.fillImageMode == CanvasFillImageMode.Sweep) {
        SweepFill(value, color, picture)
        return
    }
    val filled = Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f))
    if (picture == null) {
        Box(filled.background(color, RoundedCornerShape((element.style.cornerRadius ?: 0f).dp)))
    } else if (element.style.fillImageMode == CanvasFillImageMode.Capped) {
        Box(Modifier.fillMaxSize().drawBehind { drawImageFillBar(picture, value) })
    } else if (element.style.fillImageMode == CanvasFillImageMode.Squeeze) {
        Box(filled.drawBehind { drawImageFill(picture) })
    } else {
        Box(filled.drawBehind { clipped { drawImageFill(picture, width = element.width * density) } })
    }
}

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
