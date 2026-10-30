/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.animation.DEFAULT_TRAIL_DRAIN_PER_SECOND
import com.awakekt.awake.compose.foundation.animation.DEFAULT_TRAIL_HOLD_SECONDS
import com.awakekt.awake.core.input.NumberStepping
import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable

/** Which way a Bar fills. */
@Serializable
enum class CanvasBarDirection {
    /** Across from the left, the default. */
    LeftToRight,

    /** Across from the right. */
    RightToLeft,

    /** Up from the bottom. */
    BottomToTop,

    /** Down from the top. */
    TopToBottom,

    /** Around the element's centre, clockwise from 12 o'clock, as a pie or a ring. */
    Radial,
}

/**
 * How a Bar draws as a progress bar beyond its fill: the way it runs, a trailing fill behind it,
 * segments or ticks, and a label. A Bar with none fills across from the left, as it always has.
 *
 * Its colours are the element's: [CanvasElement.color] fills, [CanvasElement.background] and its
 * style draw behind, and [CanvasStyle.fillImage] fills in place of the colour, as
 * [CanvasStyle.fillImageMode] says.
 *
 * @property direction Which way it fills; [CanvasBarDirection.Radial] runs around the centre.
 * @property thickness A radial bar's ring width in dp; 0 fills a pie.
 * @property track The colour of the part not yet filled, drawn in the bar's shape: the whole bar,
 * or a radial bar's ring. Null leaves the element's own background.
 * @property trailColor A second fill behind the first, in this colour, that trails it when the value
 * drops, as a health bar shows damage just taken. Null draws no trail.
 * @property trailDelay How long the trail holds after the value drops, in seconds, before it drains.
 * @property trailSpeed How fast the trail drains, in the bar's whole length a second.
 * @property segments The bar as this many blocks with [segmentGap] between them, filled in turn, as a
 * stamina bar's pips; 0 or 1 is one bar. A straight bar's colour fill only.
 * @property segmentGap The gap between [segments], in dp.
 * @property ticks Marks dividing a straight bar into this many equal parts, in [tickColor]; 0 or 1
 * marks none.
 * @property tickColor The ticks' colour.
 * @property label Whether the bar shows its words centred on it: its text, its `textKey`'s or its
 * binding's `text`, or the value as a percentage when it has none. They are white, or the style's
 * `textColor`.
 */
@Serializable
data class CanvasBar(
    val direction: CanvasBarDirection = CanvasBarDirection.LeftToRight,
    @PropertyRange(min = 0.0) val thickness: Float = 0f,
    val track: String? = null,
    val trailColor: String? = null,
    @PropertyRange(min = 0.0) val trailDelay: Float = DEFAULT_TRAIL_HOLD_SECONDS,
    @PropertyRange(min = 0.0, exclusiveMin = true) val trailSpeed: Float = DEFAULT_TRAIL_DRAIN_PER_SECOND,
    @PropertyRange(min = 0.0) val segments: Int = 0,
    @PropertyRange(min = 0.0) val segmentGap: Float = DEFAULT_SEGMENT_GAP,
    @PropertyRange(min = 0.0) val ticks: Int = 0,
    val tickColor: String = DEFAULT_TICK_COLOR,
    val label: Boolean = false,
) {
    /** What is wrong with these settings, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        addAll(colourProblems("track" to track, "trailColor" to trailColor, "tickColor" to tickColor))
        if (thickness < 0f) add("thickness must not be negative")
        if (trailDelay < 0f) add("trailDelay must not be negative")
        if (trailSpeed <= 0f) add("trailSpeed must be above 0")
        if (segments < 0) add("segments must not be negative")
        if (segmentGap < 0f) add("segmentGap must not be negative")
        if (ticks < 0) add("ticks must not be negative")
    }

    /** Defaults. */
    companion object {
        /** A Bar's settings when it has none. */
        val Default = CanvasBar()

        /** The gap between segments by default, in dp. */
        const val DEFAULT_SEGMENT_GAP = 2f

        /** Ticks by default: half-transparent black, which reads on most fills. */
        const val DEFAULT_TICK_COLOR = "#00000080"
    }
}

/** The outline a Sweep or camera picture is cut to. */
@Serializable
enum class CanvasMask {
    /** The element's box, its corners rounded by its style's `cornerRadius`. */
    Box,

    /** The circle the box holds. */
    Circle,

    /** A capsule, its short sides fully round. */
    Pill,

    /** The box with its corners cut by its style's `cornerRadius`. */
    CutCorner,
}

/**
 * How a Sweep turns: a pie or ring filling [CanvasElement.value], from 0 to 1, of a full turn, as a
 * cooldown, a timer or a charge does. It fills in [CanvasElement.color], or [CanvasStyle.fillImage]
 * cut to the sweep, over its background; an action button's cooldown sweep is the same drawing.
 *
 * @property startAngle Where it starts, in degrees clockwise from 12 o'clock.
 * @property clockwise Whether it turns clockwise.
 * @property mask The outline a pie is cut to, such as a rounded square over an icon.
 * @property thickness A ring's width in dp, inside the circle the box holds; 0 fills a pie.
 * @property track The colour of the part not yet swept, in the same shape; null draws none.
 */
@Serializable
data class CanvasSweep(
    val startAngle: Float = 0f,
    val clockwise: Boolean = true,
    val mask: CanvasMask = CanvasMask.Box,
    @PropertyRange(min = 0.0) val thickness: Float = 0f,
    val track: String? = null,
) {
    /** What is wrong with these settings, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        addAll(colourProblems("track" to track))
        if (thickness < 0f) add("thickness must not be negative")
        if (!startAngle.isFinite()) add("startAngle must be a number")
    }

    /** Defaults. */
    companion object {
        /** A Sweep's settings when it has none: a pie clockwise from 12 o'clock, cut to the box. */
        val Default = CanvasSweep()
    }
}

/**
 * A Stepper's number and how it steps: minus and plus buttons around [value], kept between [min]
 * and [max] and moved [step] at a time. Holding a button repeats after [repeatDelay] seconds, every
 * [repeatInterval]. The live number is [CanvasElement.number].
 *
 * @property value The number it starts at.
 * @property min The lowest it goes.
 * @property max The highest it goes; at least [min].
 * @property step How far one press moves it; above 0.
 * @property decimals How many digits it shows after the point, from 0 to 6.
 * @property repeatDelay How long a button is held before it repeats, in seconds.
 * @property repeatInterval How often a held button repeats, in seconds; above 0.
 */
@Serializable
data class CanvasStepper(
    val value: Float = 0f,
    val min: Float = 0f,
    val max: Float = DEFAULT_MAX,
    @PropertyRange(min = 0.0, exclusiveMin = true) val step: Float = 1f,
    @PropertyRange(min = 0.0, max = 6.0) val decimals: Int = 0,
    @PropertyRange(min = 0.0) val repeatDelay: Float = NumberStepping.REPEAT_DELAY_SECONDS,
    @PropertyRange(min = 0.0, exclusiveMin = true) val repeatInterval: Float = NumberStepping.REPEAT_INTERVAL_SECONDS,
) {
    /** What is wrong with these settings, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (!(min.isFinite() && max.isFinite() && value.isFinite())) add("value, min and max must be numbers")
        if (max < min) add("max must be at least min")
        if (value !in min..max) add("value must be between min and max")
        if (step <= 0f) add("step must be above 0")
        if (decimals !in 0..NumberStepping.MAX_DECIMALS) add("decimals must be between 0 and ${NumberStepping.MAX_DECIMALS}")
        if (repeatDelay < 0f) add("repeatDelay must not be negative")
        if (repeatInterval <= 0f) add("repeatInterval must be above 0")
    }

    /** Defaults. */
    companion object {
        /** A Stepper's settings when it has none: 0 to 100 in ones. */
        val Default = CanvasStepper()

        /** The upper bound by default. */
        const val DEFAULT_MAX = 100f
    }
}

internal fun colourProblems(vararg colours: Pair<String, String?>): List<String> =
    colours.mapNotNull { (name, value) -> if (value != null && !isCanvasColor(value)) "$name \"$value\" must be #RRGGBB or #RRGGBBAA" else null }
