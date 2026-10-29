/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.StyleState
import com.awakekt.awake.compose.foundation.style.rememberStyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.graphics.CircleShape
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.semantics.testTag
import kotlin.time.TimeMark

/**
 * What a custom [CanvasSlot] is given: the control it is a part of, which part, and the state the
 * control is drawn in. Everything else a part might show, such as a stick's [CanvasElement.stickX],
 * a button's [CanvasElement.cooldown] or [CanvasElement.aimX], is read from [element].
 *
 * @property element The control.
 * @property slot The part, such as `joystick.knob` or `button.cooldown`.
 * @property state The state the control is drawn in.
 */
class CanvasSlotInfo(val element: CanvasElement, val slot: String, val state: CanvasControlState)

/** A composable that draws a [CanvasSlot] whose [CanvasSlot.custom] names it, in the slot's box. */
typealias CanvasSlotContent = context(Composer)
BoxScope.(CanvasSlotInfo) -> Unit

/** The custom slot content a canvas was given, by name. */
internal val LocalCanvasSlotContent = compositionLocalOf<Map<String, CanvasSlotContent>> { emptyMap() }

/** The names of every slot, as [CanvasSlotInfo.slot] gives them. */
object CanvasSlots {
    /** A Joystick's pad. */
    const val JOYSTICK_BASE = "joystick.base"

    /** A Joystick's knob. */
    const val JOYSTICK_KNOB = "joystick.knob"

    /** A Joystick's sprint ring. */
    const val JOYSTICK_SPRINT_RING = "joystick.sprintRing"

    /** A Joystick's direction arrow. */
    const val JOYSTICK_ARROWS = "joystick.arrows"

    /** An action button's background. */
    const val BUTTON_BACKGROUND = "button.background"

    /** An action button's icon. */
    const val BUTTON_ICON = "button.icon"

    /** An action button's label. */
    const val BUTTON_LABEL = "button.label"

    /** An action button's cooldown sweep. */
    const val BUTTON_COOLDOWN = "button.cooldown"

    /** An action button's charge badge. */
    const val BUTTON_CHARGES = "button.charges"

    /** An aimed button's cursor. */
    const val BUTTON_AIM_CURSOR = "button.aimCursor"

    /** An aimed button's reach. */
    const val BUTTON_AIM_RANGE = "button.aimRange"

    /** An aimed button's cancel zone. */
    const val BUTTON_CANCEL_ZONE = "button.cancelZone"

    /** A LookArea's thumb feedback. */
    const val LOOK_FEEDBACK = "look.feedback"
}

/**
 * Where one slot of one control is in its fade from the state it was drawn in to the one it is in
 * now, and when its frames started. It outlives the frames it spans.
 */
internal class SlotTransition(var state: CanvasControlState, var since: TimeMark) {
    var previous: CanvasControlState? = null
    var started: TimeMark = since
}

/** [state]'s override on this slot, or its fallback's, or null when none changes it. */
internal fun CanvasSlot.overrideFor(state: CanvasControlState): CanvasSlotOverride? {
    var at: CanvasControlState? = state
    while (at != null) {
        states[at]?.let { return it }
        at = at.fallback
    }
    return null
}

/**
 * [slot], one part of [element], drawn in [state] in [modifier]'s box: its style with the state's
 * over it, its frames, or the custom content it names, fading in from the state it was drawn in
 * before over its [CanvasSlot.transitionSeconds]. [content] draws over it, such as a badge's number.
 */
context(_: Composer)
internal fun Slot(
    element: CanvasElement,
    name: String,
    slot: CanvasSlot,
    state: CanvasControlState,
    modifier: Modifier,
    images: Map<String, ImageBitmap>,
    round: Boolean = false,
    content: (
        context(Composer)
        BoxScope.() -> Unit
    )? = null,
) {
    val transition = element.slotTransition(name, state)
    val seconds = transition.since.elapsedNow().inWholeMicroseconds / MICROS
    val progress = if (slot.transitionSeconds <= 0f) 1f else (seconds / slot.transitionSeconds).coerceIn(0f, 1f)
    val previous = transition.previous
    val framesAt = transition.started.elapsedNow().inWholeMicroseconds / MICROS
    // Keyed by state, so each layer keeps its own remembered style state as the fade swaps them.
    val layer = SlotLayer(element, name, slot, modifier, images, round, framesAt, content)
    if (previous != null && progress < 1f) key(previous) { layer.draw(previous, 1f - progress) }
    key(state) { layer.draw(state, progress) }
}

/** One slot's look, drawn once for each state it shows in during a fade. */
private class SlotLayer(
    val element: CanvasElement,
    val name: String,
    val slot: CanvasSlot,
    val modifier: Modifier,
    val images: Map<String, ImageBitmap>,
    val round: Boolean,
    val framesAt: Float,
    val content: (
        context(Composer)
        BoxScope.() -> Unit
    )?,
) {
    /** The slot in [state], at [fade] of its opacity. */
    context(_: Composer)
    fun draw(state: CanvasControlState, fade: Float) {
        val override = slot.overrideFor(state)
        if (override?.hidden ?: slot.hidden) return
        val custom = LocalCanvasSlotContent.current[slot.custom]
        val styleState = if (custom == null) rememberStyleState(element.interactions) else null
        val style = if (custom == null) override?.style?.over(slot.style) ?: slot.style else null
        val layer = layerModifier(override, fade, style, styleState)
        if (custom != null) {
            Box(layer, contentAlignment = Alignment.Center) {
                custom(CanvasSlotInfo(element, name, state))
                if (content != null) content()
            }
            return
        }
        val frame = (override?.frames ?: slot.frames)?.imageAt(framesAt)?.fill(images)
        val painted = Style {
            style?.applyTo(this, images)
            frame?.let(::backgroundImage)
        }
        Box(layer.styleable(checkNotNull(styleState), painted), contentAlignment = Alignment.Center) { if (content != null) content() }
    }

    private fun layerModifier(
        override: CanvasSlotOverride?,
        fade: Float,
        style: CanvasStyle?,
        styleState: StyleState?,
    ): Modifier {
        val alpha = (override?.alpha ?: 1f) * fade
        val scale = (override?.scale ?: 1f) * (if (style != null && styleState != null) style.stateScale(styleState) else 1f)
        var layer = modifier.testTag("canvas-slot-$name")
        if (alpha < 1f || scale != 1f) layer = layer.graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale)
        return if (slot.circle ?: round) layer.clip(CircleShape) else layer
    }
}

/** This slot's fade for [name] on this element, its state moved on to [state] when that changed. */
internal fun CanvasElement.slotTransition(name: String, state: CanvasControlState): SlotTransition {
    val transition = slotTransitions.getOrPut(name) { SlotTransition(state, clock.markNow()) }
    if (transition.state != state) {
        transition.previous = transition.state
        transition.state = state
        transition.since = clock.markNow()
        transition.started = transition.since
    }
    return transition
}

/** This style over [below]: each property it sets replaces [below]'s, and one it leaves out keeps it. */
@Suppress("CyclomaticComplexMethod") // One `?:` per property: nothing here branches.
internal fun CanvasStyle.over(below: CanvasStyle?): CanvasStyle = if (below == null) {
    this
} else {
    CanvasStyle(
        background = background ?: below.background,
        gradient = gradient ?: below.gradient,
        image = image ?: below.image,
        fillImage = fillImage ?: below.fillImage,
        fillImageMode = if (fillImageMode != CanvasFillImageMode.Cut) fillImageMode else below.fillImageMode,
        cornerRadius = cornerRadius ?: below.cornerRadius,
        borderWidth = borderWidth ?: below.borderWidth,
        borderColor = borderColor ?: below.borderColor,
        shadow = shadow ?: below.shadow,
        textColor = textColor ?: below.textColor,
        textShadow = textShadow ?: below.textShadow,
        textOutline = textOutline ?: below.textOutline,
        alpha = alpha ?: below.alpha,
        hovered = hovered ?: below.hovered,
        pressed = pressed ?: below.pressed,
        disabled = disabled ?: below.disabled,
        rotation = rotation ?: below.rotation,
        linkColor = linkColor ?: below.linkColor,
        transitionSeconds = transitionSeconds ?: below.transitionSeconds,
    )
}

private const val MICROS = 1_000_000f
