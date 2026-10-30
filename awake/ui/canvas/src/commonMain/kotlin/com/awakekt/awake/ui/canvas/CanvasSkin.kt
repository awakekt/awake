/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable

/**
 * One visual part of an on-screen control, such as a stick's knob or a button's cooldown sweep. The
 * control owns how the part behaves and where it goes; the slot owns how it looks, filled in one of
 * three ways:
 *
 * - a [style]: colour, gradient, corner radius, border, shadow and opacity, with [circle] for a round
 *   part and [size] for a part that has its own;
 * - images: the style's `image`, a single picture, a nine-slice frame or a region of an atlas, or
 *   [frames] played as an animation, and a different image in any state through [states];
 * - [custom] content: a composable the host registered under that name, given the control's live state.
 *
 * Each [CanvasControlState] may change the part through [states], faded in over [transitionSeconds].
 * A colour written `@name` is the theme's colour of that name, and an image whose `path` is `@name`
 * is the theme's image of that name, so one change to the theme restyles every control.
 *
 * @property style How the part looks in every state.
 * @property size The part's size in dp, for a part that has its own, such as a knob or a badge; null
 * keeps the control's own.
 * @property circle Whether the part is round, cut to a circle; null keeps the control's own shape.
 * @property frames Images played in turn, as a sprite animation, over [style]'s.
 * @property custom The name of a composable the host registered for the canvas, drawn in place of
 * [style] and [frames]; empty draws them.
 * @property states What changes in each state: its style over [style], a scale, an opacity, frames,
 * or hiding the part.
 * @property transitionSeconds How long a change of state takes to fade in; 0 changes at once.
 * @property hidden Whether the part is not drawn at all, unless a state shows it.
 */
@Serializable
data class CanvasSlot(
    val style: CanvasStyle? = null,
    @PropertyRange(min = 0.0) val size: Float? = null,
    val circle: Boolean? = null,
    val frames: CanvasFrames? = null,
    val custom: String = "",
    val states: Map<CanvasControlState, CanvasSlotOverride> = emptyMap(),
    @PropertyRange(min = 0.0) val transitionSeconds: Float = 0f,
    val hidden: Boolean = false,
) {
    /** What is wrong with this slot, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        style?.tokenProblems()?.forEach { add("style.$it") }
        // Qualified: inside buildList, a bare `size` is the list's own.
        val size = this@CanvasSlot.size
        if (size != null && size < 0f) add("size must not be negative")
        frames?.problems()?.forEach { add("frames.$it") }
        if (transitionSeconds < 0f) add("transitionSeconds must not be negative")
        for ((state, override) in states) override.problems().forEach { add("states.${state.name}.$it") }
    }
}

/**
 * What one [CanvasControlState] changes about a [CanvasSlot]. A field left out keeps the slot's own.
 *
 * @property style Drawn over the slot's style: a property set here replaces the slot's.
 * @property scale How much bigger the part is, about its centre: 1.1 for a press that swells it.
 * @property alpha The part's opacity, from 0 to 1.
 * @property frames Images played in turn in this state, such as a button's press animation.
 * @property hidden Whether the part is hidden in this state, or shown when the slot hides it otherwise.
 */
@Serializable
data class CanvasSlotOverride(
    val style: CanvasStyle? = null,
    @PropertyRange(min = 0.0) val scale: Float? = null,
    @PropertyRange(min = 0.0, max = 1.0) val alpha: Float? = null,
    val frames: CanvasFrames? = null,
    val hidden: Boolean? = null,
) {
    internal fun problems(): List<String> = buildList {
        style?.tokenProblems()?.forEach { add("style.$it") }
        if (scale != null && scale < 0f) add("scale must not be negative")
        if (alpha != null && alpha !in 0f..1f) add("alpha must be between 0 and 1")
        frames?.problems()?.forEach { add("frames.$it") }
    }
}

/**
 * Images a slot plays in turn, as a sprite or a flipbook: each the whole picture or a region of an
 * atlas, as a [CanvasImage] says.
 *
 * @property images The images, in order.
 * @property fps How many images a second it plays; above 0.
 * @property loop Whether it starts again after the last; false holds the last.
 */
@Serializable
data class CanvasFrames(
    val images: List<CanvasImage> = emptyList(),
    @PropertyRange(min = 0.0, exclusiveMin = true) val fps: Float = DEFAULT_FPS,
    val loop: Boolean = true,
) {
    internal fun problems(): List<String> = buildList {
        if (fps <= 0f) add("fps must be above 0")
        images.forEachIndexed { index, image -> image.tokenProblems().forEach { add("images[$index].$it") } }
    }

    /** The image showing [seconds] after the frames started, or null when there are none. */
    fun imageAt(seconds: Float): CanvasImage? {
        if (images.isEmpty()) return null
        val step = (seconds.coerceAtLeast(0f) * fps).toInt()
        return images[if (loop) step % images.size else step.coerceAtMost(images.size - 1)]
    }

    private companion object {
        const val DEFAULT_FPS = 12f
    }
}

/**
 * The state an on-screen control is drawn in, which a [CanvasSlot] may change its look for. One at a
 * time, the first that holds in this order: disabled, cancelling, aiming, held, pressed, cooling
 * down, ready, idle. A state a slot does not change falls back to the next broader one: held to
 * pressed, cancelling to aiming and then pressed, and every other to idle.
 */
@Serializable
enum class CanvasControlState {
    /** Nobody is touching it. */
    Idle,

    /** A thumb is on it. */
    Pressed,

    /** A thumb has been on it past its long-press time. */
    Held,

    /** It cannot be used: a button that is not enabled. */
    Disabled,

    /** A button's cooldown is running with no charge to spend. */
    CoolingDown,

    /** A button just became usable again, for a short pulse. */
    Ready,

    /** A button is being dragged to aim. */
    Aiming,

    /** An aimed button is over its cancel zone, so letting go cancels. */
    Cancelling,

    ;

    /** The broader state a slot falls back to when it does not change this one; null for [Idle]. */
    internal val fallback: CanvasControlState?
        get() = when (this) {
            Idle -> null
            Held -> Pressed
            Cancelling -> Aiming
            Aiming -> Pressed
            else -> Idle
        }
}

/**
 * The slots of a Joystick.
 *
 * @property base The pad the knob moves over, the size of the stick's reach.
 * @property knob The part under the thumb.
 * @property sprintRing Drawn over the pad while the stick is sprinting.
 * @property arrows Drawn at the pad's edge on the side the stick is pushed, turned to point that way;
 * its picture points right before it turns.
 */
@Serializable
data class CanvasJoystickSkin(
    val base: CanvasSlot? = null,
    val knob: CanvasSlot? = null,
    val sprintRing: CanvasSlot? = null,
    val arrows: CanvasSlot? = null,
)

/**
 * The slots of an action button.
 *
 * @property background Behind the rest, filling the button.
 * @property icon A picture in the button's middle, [CanvasSlot.size] across.
 * @property label The button's words: its style's `textColor`, `textShadow` and `textOutline`.
 * @property cooldown The sweep over the button while it cools down: its style's `background`, or its
 * `image` cut to the sweep.
 * @property charges The badge holding the charges left, at the button's top-right corner.
 * @property aimCursor The cursor at the aim while it is dragged.
 * @property aimRange The ring of the aim's reach around the button.
 * @property cancelZone The aim's cancel zone.
 */
@Serializable
data class CanvasButtonSkin(
    val background: CanvasSlot? = null,
    val icon: CanvasSlot? = null,
    val label: CanvasSlot? = null,
    val cooldown: CanvasSlot? = null,
    val charges: CanvasSlot? = null,
    val aimCursor: CanvasSlot? = null,
    val aimRange: CanvasSlot? = null,
    val cancelZone: CanvasSlot? = null,
)

/**
 * The slots of a LookArea.
 *
 * @property feedback Drawn under each thumb on the area while it swipes or pinches.
 */
@Serializable
data class CanvasLookSkin(val feedback: CanvasSlot? = null)

/**
 * How every kind of on-screen control looks, slot by slot. A slot left out keeps the one beneath:
 * an element's own skin over its canvas's theme, over the theme's preset, over the control's plain
 * look from its own colours.
 *
 * @property joystick A Joystick's slots.
 * @property button An action button's slots.
 * @property look A LookArea's slots.
 */
@Serializable
data class CanvasControlSkin(
    val joystick: CanvasJoystickSkin? = null,
    val button: CanvasButtonSkin? = null,
    val look: CanvasLookSkin? = null,
) {
    /** What is wrong with this skin, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        joystick?.let { skin ->
            slotProblems("joystick", "base" to skin.base, "knob" to skin.knob, "sprintRing" to skin.sprintRing, "arrows" to skin.arrows)
        }
        button?.let { skin ->
            slotProblems(
                "button",
                "background" to skin.background, "icon" to skin.icon, "label" to skin.label, "cooldown" to skin.cooldown,
                "charges" to skin.charges, "aimCursor" to skin.aimCursor, "aimRange" to skin.aimRange, "cancelZone" to skin.cancelZone,
            )
        }
        look?.feedback?.problems()?.forEach { add("look.feedback.$it") }
    }

    private fun MutableList<String>.slotProblems(control: String, vararg slots: Pair<String, CanvasSlot?>) {
        for ((name, slot) in slots) slot?.problems()?.forEach { add("$control.$name.$it") }
    }
}

/**
 * How a canvas's on-screen controls look, all at once: a built-in [preset] to start from, its tokens,
 * and a [skin] over the preset's. A game ships several, such as a light, a dark and a high-contrast
 * theme, and switches between them by setting [CanvasControls.theme].
 *
 * @property preset The built-in look the theme starts from; [CanvasThemePreset.None] starts from each
 * control's plain look.
 * @property colors Colour tokens by name, each `#RRGGBB` or `#RRGGBBAA`, which a slot's colour writes
 * as `@name`; they add to and replace the preset's.
 * @property images Image tokens by name, which a slot's image names with the path `@name`; they add to
 * and replace the preset's, as a project's own art replaces the textured preset's.
 * @property skin The theme's own slots, over the preset's.
 * @property transitionSeconds Default seconds a [CanvasStyle] takes to interpolate between its
 * states on this canvas when its own [CanvasStyle.transitionSeconds] is null; null or 0 changes at
 * once.
 */
@Serializable
data class CanvasControlTheme(
    val preset: CanvasThemePreset = CanvasThemePreset.MinimalFlat,
    val colors: Map<String, String> = emptyMap(),
    val images: Map<String, CanvasImage> = emptyMap(),
    val skin: CanvasControlSkin = CanvasControlSkin(),
    @PropertyRange(min = 0.0) val transitionSeconds: Float? = null,
) {
    /** [CanvasControlTheme] as it was before `transitionSeconds`: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        preset: CanvasThemePreset = CanvasThemePreset.MinimalFlat,
        colors: Map<String, String> = emptyMap(),
        images: Map<String, CanvasImage> = emptyMap(),
        skin: CanvasControlSkin = CanvasControlSkin(),
    ) : this(preset, colors, images, skin)

    /** [copy] as it was before `transitionSeconds`: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        preset: CanvasThemePreset = this.preset,
        colors: Map<String, String> = this.colors,
        images: Map<String, CanvasImage> = this.images,
        skin: CanvasControlSkin = this.skin,
    ): CanvasControlTheme = copy(preset, colors, images, skin)

    /** What is wrong with this theme, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        for ((name, color) in colors) {
            if (!TOKEN_NAME.matches(name)) add("colors \"$name\" must be letters, digits, - or _")
            if (!isCanvasColor(color)) add("colors.$name \"$color\" must be #RRGGBB or #RRGGBBAA")
        }
        for ((name, image) in images) {
            if (!TOKEN_NAME.matches(name)) add("images \"$name\" must be letters, digits, - or _")
            image.problems().forEach { add("images.$name.$it") }
        }
        if (transitionSeconds != null && !(transitionSeconds >= 0f && transitionSeconds.isFinite())) add("transitionSeconds must not be negative")
        skin.problems().forEach { add("skin.$it") }
        unknownTokens().forEach { add("skin names the token @$it, which neither the theme nor its preset has") }
    }
}

/** A built-in look for on-screen controls, all editable as data through [CanvasControlTheme]. */
@Serializable
enum class CanvasThemePreset {
    /** No preset: each control keeps its plain look from its own colours. */
    None,

    /** Flat, translucent shapes with a bright accent. */
    MinimalFlat,

    /** Frosted panes with a soft border and shadow. */
    Glass,

    /**
     * Pictures: a pad, a knob and a button frame, as image tokens (`pad`, `knob`, `button`,
     * `buttonPressed`) naming files under `controls/` the project provides, or the project's own
     * images in their place.
     */
    Textured,
}

/** What a token name may be. */
internal val TOKEN_NAME = Regex("^[A-Za-z0-9_-]+$")

/** A colour problem about a `@name` token, which a skin may write wherever it writes a colour. */
private val TOKEN_COLOUR_PROBLEM = Regex("\"@[A-Za-z0-9_-]+\" must be #RRGGBB or #RRGGBBAA")

/** This style's problems, a `@name` colour token being no problem. */
internal fun CanvasStyle.tokenProblems(): List<String> = problems().filterNot(TOKEN_COLOUR_PROBLEM::containsMatchIn)

/** This image's problems, a `@name` tint token being no problem. */
internal fun CanvasImage.tokenProblems(): List<String> = problems().filterNot(TOKEN_COLOUR_PROBLEM::containsMatchIn)
