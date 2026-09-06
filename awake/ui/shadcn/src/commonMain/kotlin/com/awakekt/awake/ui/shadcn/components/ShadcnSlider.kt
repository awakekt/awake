/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.animation.animateFloat
import com.awakekt.awake.compose.foundation.focusable
import com.awakekt.awake.compose.foundation.gestures.draggable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.heightIn
import com.awakekt.awake.compose.foundation.style.rememberStyleState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.input.key.onKeyEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.layout.onSizeChanged
import com.awakekt.awake.compose.ui.node.PointerInputNode
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's slider: a muted track, a primary range up to the value, and a bordered round thumb.
 *
 * Ported from all three sources, as `awake-ui-authoring` requires, and each contributed something:
 *
 * - **Upstream `slider.tsx`** fixes the values -- track `h-1.5` (6px) `rounded-full bg-muted`, range
 *   `bg-primary`, thumb `size-4` (16px) `rounded-full border-primary`. It also settles the thumb
 *   fill: upstream is **`bg-white`**, not `bg-background`. Those are the same colour in light mode
 *   and different in dark, and `shadcn-compose` uses `colors.background` there -- a divergence not
 *   carried across.
 * - **`shadcn-compose`** contributed the shape of the split: track, range and thumb are three styles
 *   rather than one, which is what lets the range be a plain overlay instead of a track variant.
 * - **`ui-headless`** contributed the track inset, which neither of the others knows about: a thumb
 *   centred at fraction 0 extends half its width past the widget and is clipped by any clipping
 *   parent. Reported as "knob cut when reach start or end"; see [shadcnSliderTrack].
 *
 * Takes [onValueChange] rather than returning the next value. A drag or key press that happened
 * arrives before this build runs (see below), so the new value is known during this same frame --
 * what is drawn uses it immediately, and [onValueChange] fires it out to the caller's own state at
 * the same time, the callback shape every other interactive recipe in this file settles on.
 */
context(_: Composer)
fun ShadcnSlider(
    value: Float,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit = {},
) {
    val theme = shadcnTheme
    val state = remember { SliderDragState() }
    val interaction = remember { InteractionSource() }
    val styleState = rememberStyleState(interaction, enabled = enabled)
    val focusRingAlpha = animateFloat(if (styleState.isFocused) 1f else 0f)

    // A pointer delta or an arrow-key step arrives during input dispatch, which is before this
    // build runs, so one that happened is already waiting here. Consuming it now rather than on the
    // next frame is what keeps the thumb under the pointer (or the keyboard) instead of one frame
    // behind it.
    val next = if (enabled && state.pendingPx != 0f && state.trackWidthPx > 0f) {
        val track = ShadcnSliderTrack(0f, state.trackWidthPx)
        val moved = shadcnSliderValueAt(
            pointerX = shadcnSliderFraction(value, min, max) * track.width + state.pendingPx,
            track = track,
            min = min,
            max = max,
        )
        state.pendingPx = 0f
        val snapped = shadcnSliderSnap(moved, min, max, steps)
        onValueChange(snapped)
        snapped
    } else if (enabled && state.pendingKeyDelta != 0f) {
        val moved = (value + state.pendingKeyDelta).coerceIn(min, max)
        state.pendingKeyDelta = 0f
        val snapped = shadcnSliderSnap(moved, min, max, steps)
        onValueChange(snapped)
        snapped
    } else {
        state.pendingPx = 0f
        state.pendingKeyDelta = 0f
        value.coerceIn(min, max)
    }

    val fraction = shadcnSliderFraction(next, min, max)

    Spacer(
        // Tall enough for the thumb, which the Canvas paints inside its own bounds. Without a
        // default the control measures zero high and draws nothing wherever a caller does not
        // happen to size it -- the parity fixture found exactly that. The reference's 6px is
        // Radix's track alone; its thumb is absolutely positioned and overflows the root.
        modifier
            .heightIn(min = SliderThumbSize)
            .onSizeChanged { width, _ ->
                state.trackWidthPx = shadcnSliderTrack(0f, width.toFloat(), state.thumbPx).width
            }
            .draggable { dx, _ -> if (enabled) state.pendingPx += dx.toFloat() }
            .focusable(enabled = enabled, interactionSource = interaction)
            .onKeyEvent { handleSliderKey(it, min, max, steps, state) }
            .focusRing(
                alpha = focusRingAlpha,
                width = FieldFocusRingWidth,
                color = theme.palette.ring.withAlpha(0.5f),
                cornerRadius = SliderTrackHeight / 2f,
            )
            // drawBehind, not drawWithCache: the cache rebuilds on size, density and layout
            // direction only, and a drag changes none of them. It captured `fraction`, so the
            // range and thumb stayed where the first frame put them while onValueChange kept
            // reporting the new value -- a slider whose number moves and whose bar does not.
            // Nothing here is worth caching anyway; it is six multiplications, not a tessellation.
            .drawBehind {
                val thumb = SliderThumbSize.value * density
                state.thumbPx = thumb
                val track = shadcnSliderTrack(0f, width.toFloat(), thumb)
                val trackH = SliderTrackHeight.value * density
                val trackY = (height - trackH) / 2f
                val ring = ThumbRingWidth.value * density
                withAlpha(if (enabled) 1f else DISABLED_ALPHA) {
                    drawRoundedRect(
                        x = track.x,
                        y = trackY,
                        width = track.width,
                        height = trackH,
                        color = theme.palette.muted,
                        radius = trackH / 2f,
                    )
                    drawRoundedRect(
                        x = track.x,
                        y = trackY,
                        width = track.width * fraction,
                        height = trackH,
                        color = theme.palette.primary,
                        radius = trackH / 2f,
                    )
                    drawSliderThumb(track, fraction, thumb, ring, theme.palette.primary)
                }
            },
    )
}

/**
 * shadcn's range slider: one track shared by two thumbs, with the primary fill spanning between
 * them instead of from the start.
 *
 * The old "range slider" was two full-width [ShadcnSlider]s stacked in a column -- two tracks, two
 * independently-clamped thumbs -- which is a different control from upstream's multi-value `Slider`
 * and reads that way: the span between the values is never drawn as one piece. This shares
 * [shadcnSliderTrack]'s inset and [shadcnNearerThumb]'s pointer-to-thumb resolution with the
 * single-value recipe so both stay the same width of control under the same pointer.
 *
 * Keyboard stepping is deliberately not here: two thumbs need a per-thumb focus target to know
 * which one an arrow key moves, and this control has one drawn surface, not two. Add it when a
 * caller needs it.
 */
context(_: Composer)
fun ShadcnRangeSlider(
    start: Float,
    end: Float,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onStartChange: (Float) -> Unit = {},
    onEndChange: (Float) -> Unit = {},
) {
    val theme = shadcnTheme
    val state = remember { RangeSliderDragState() }

    val startFraction = shadcnSliderFraction(start, min, max)
    val endFraction = shadcnSliderFraction(end, min, max)

    // Same before-this-build timing as ShadcnSlider: a press picks the nearer thumb and an absolute
    // pointer x, a move only updates the x, and both are resolved into a value here so the draw
    // below and the caller's state agree within the same frame.
    val resolved = state.pendingXPx?.let { x ->
        if (!enabled || state.slotWidthPx <= 0f) {
            state.pendingXPx = null
            state.activeThumb = null
            return@let null
        }
        val track = shadcnSliderTrack(0f, state.slotWidthPx, state.thumbPx)
        val moved = shadcnSliderValueAt(x.toFloat(), track, min, max)
        state.pendingXPx = null
        when (state.activeThumb) {
            ShadcnSliderThumb.Start -> {
                val snapped = shadcnSliderSnap(moved, min, max, steps).coerceAtMost(end)
                onStartChange(snapped)
                snapped to end
            }
            ShadcnSliderThumb.End -> {
                val snapped = shadcnSliderSnap(moved, min, max, steps).coerceAtLeast(start)
                onEndChange(snapped)
                start to snapped
            }
            null -> null
        }
    } ?: (start.coerceIn(min, max) to end.coerceIn(min, max))
    val (resolvedStart, resolvedEnd) = resolved

    val resolvedStartFraction = shadcnSliderFraction(resolvedStart, min, max)
    val resolvedEndFraction = shadcnSliderFraction(resolvedEnd, min, max)

    Spacer(
        modifier
            .heightIn(min = SliderThumbSize)
            .onSizeChanged { width, _ -> state.slotWidthPx = width.toFloat() }
            .rangeDraggable(
                onPress = { x ->
                    if (enabled && state.slotWidthPx > 0f) {
                        val track = shadcnSliderTrack(0f, state.slotWidthPx, state.thumbPx)
                        val startThumbX = track.x + track.width * startFraction
                        val endThumbX = track.x + track.width * endFraction
                        state.activeThumb = shadcnNearerThumb(x.toFloat(), startThumbX, endThumbX)
                        state.pendingXPx = x
                    }
                },
                onDrag = { x -> if (enabled) state.pendingXPx = x },
            )
            // Same reason as ShadcnSlider: the captured fractions change on drag, the size does not.
            .drawBehind {
                val thumb = SliderThumbSize.value * density
                state.thumbPx = thumb
                val track = shadcnSliderTrack(0f, width.toFloat(), thumb)
                val trackH = SliderTrackHeight.value * density
                val trackY = (height - trackH) / 2f
                val ring = ThumbRingWidth.value * density
                withAlpha(if (enabled) 1f else DISABLED_ALPHA) {
                    drawRoundedRect(
                        x = track.x,
                        y = trackY,
                        width = track.width,
                        height = trackH,
                        color = theme.palette.muted,
                        radius = trackH / 2f,
                    )
                    val spanX = track.x + track.width * resolvedStartFraction
                    val spanW = track.width * (resolvedEndFraction - resolvedStartFraction)
                    drawRoundedRect(
                        x = spanX,
                        y = trackY,
                        width = spanW,
                        height = trackH,
                        color = theme.palette.primary,
                        radius = trackH / 2f,
                    )
                    drawSliderThumb(track, resolvedStartFraction, thumb, ring, theme.palette.primary)
                    drawSliderThumb(track, resolvedEndFraction, thumb, ring, theme.palette.primary)
                }
            },
    )
}

/**
 * Steps [state]'s pending value by one arrow-key press, the way Radix's slider is keyboard-operable.
 *
 * Home/End are skipped: they jump to an absolute value rather than stepping one, which does not fit
 * the delta this shares with dragging. Add them if a caller needs the jump.
 */
private fun handleSliderKey(event: KeyEvent, min: Float, max: Float, steps: Int, state: SliderDragState): Boolean {
    if (event.type != KeyEventType.Down) return false
    val step = shadcnSliderKeyStep(min, max, steps)
    when (event.key) {
        Key.ArrowLeft, Key.ArrowDown -> state.pendingKeyDelta -= step
        Key.ArrowRight, Key.ArrowUp -> state.pendingKeyDelta += step
        else -> return false
    }
    return true
}

/**
 * The thumb: a primary-filled ring with the fill inset by [ThumbRingWidth] -- `border-primary
 * border bg-white`, without which a white circle alone is invisible on a light track.
 */
private fun DrawScope.drawSliderThumb(
    track: ShadcnSliderTrack,
    fraction: Float,
    thumb: Float,
    ring: Float,
    primary: Color,
) {
    val thumbX = track.x + track.width * fraction - thumb / 2f
    val thumbY = (height - thumb) / 2f
    drawRoundedRect(
        x = thumbX,
        y = thumbY,
        width = thumb,
        height = thumb,
        color = primary,
        radius = thumb / 2f,
    )
    drawRoundedRect(
        x = thumbX + ring,
        y = thumbY + ring,
        width = thumb - ring * 2f,
        height = thumb - ring * 2f,
        color = ThumbFill,
        radius = (thumb - ring * 2f) / 2f,
    )
}

/**
 * What survives between frames for a single-value slider: the drag or key step that happened, and
 * the track width to convert a drag delta with.
 *
 * A class rather than four `remember`s because they are one fact -- and because `remember`'s slots
 * are positional, so four of them in a row is four chances to add a fifth in the wrong place.
 */
private class SliderDragState {
    var pendingPx: Float = 0f
    var pendingKeyDelta: Float = 0f
    var trackWidthPx: Float = 0f
    var thumbPx: Float = 0f
}

/**
 * What survives between frames for a range slider: which thumb a press picked, and the absolute
 * pointer x it (or the move after it) reported.
 *
 * Absolute x rather than a delta -- unlike the single-value drag, a range press has to resolve
 * which of two thumbs it means before there is any delta to apply, so the position itself is kept.
 */
private class RangeSliderDragState {
    var activeThumb: ShadcnSliderThumb? = null
    var pendingXPx: Int? = null
    var slotWidthPx: Float = 0f
    var thumbPx: Float = 0f
}

/** `border`. */
private val ThumbRingWidth: Dp = 1.dp

/** shadcn `h-1.5`. */
private val SliderTrackHeight: Dp = 6.dp

/** shadcn `size-4`. */
private val SliderThumbSize: Dp = 16.dp

/** shadcn's root carries `data-[disabled]:opacity-50`. */
private const val DISABLED_ALPHA = 0.5f

/**
 * Upstream's thumb is `bg-white`, literally -- not `bg-background`.
 *
 * The two agree in light mode and part in dark, where upstream keeps a white thumb against the dark
 * track. `shadcn-compose` uses `colors.background` here; upstream settles it.
 */
private val ThumbFill = Color.White

/**
 * Reports the absolute local x of a press, then the absolute local x of every move while this node
 * holds the pointer.
 *
 * [Modifier.draggable] only reports deltas, which is right for a single thumb that always moves
 * from wherever it already is. A range slider's press has to pick a thumb first, which needs the
 * press position itself, not a delta from it.
 */
private fun Modifier.rangeDraggable(onPress: (x: Int) -> Unit, onDrag: (x: Int) -> Unit): Modifier =
    this then RangeDraggableElement(onPress, onDrag)

private class RangeDraggableElement(
    private val onPress: (Int) -> Unit,
    private val onDrag: (Int) -> Unit,
) : ModifierNodeElement<RangeDraggableNode>() {
    override fun create(): RangeDraggableNode = RangeDraggableNode()

    override fun update(node: RangeDraggableNode) {
        node.onPress = onPress
        node.onDrag = onDrag
    }

    override fun toString(): String = "rangeDraggable()"
}

private class RangeDraggableNode :
    Modifier.Node(),
    PointerInputNode {
    lateinit var onPress: (Int) -> Unit
    lateinit var onDrag: (Int) -> Unit

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main) return
        when (event.type) {
            PointerEventType.Press -> {
                onPress(event.x)
                event.consume()
            }
            PointerEventType.Move -> if (event.isCaptureHolder) {
                onDrag(event.x)
                event.consume()
            }
            else -> Unit
        }
    }

    override fun toString(): String = "rangeDraggable()"
}
