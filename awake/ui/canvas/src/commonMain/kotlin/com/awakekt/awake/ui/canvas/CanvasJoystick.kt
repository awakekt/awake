/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a Joystick moves and what it reports: where its pad sits, how far its knob reaches, how a
 * deflection becomes [CanvasElement.stickX] and [CanvasElement.stickY], and how it looks while
 * nobody touches it. A Joystick with none behaves as [Default] does.
 *
 * The element's own box is where a thumb may land. In [CanvasJoystickMode.Fixed] the pad is centred
 * in it; in the other modes the box is a zone, such as the left half of the screen, and the pad
 * appears where the thumb lands.
 *
 * A deflection is worked out in order: the knob's distance from the pad's centre as a share of
 * [radius]; nothing inside [deadZone], and the rest stretched back to 0 to 1; shaped by [response],
 * or by [curve] when one is set; then turned to the nearest of [directions].
 *
 * @property mode Where the pad sits: still, where the thumb lands, or following the thumb.
 * @property radius How far, in dp, the knob reaches from the pad's centre. 0 takes half the box's
 * shorter side in [CanvasJoystickMode.Fixed], and [DEFAULT_ZONE_RADIUS] in the other modes.
 * @property restX Where across its zone a floating or dynamic pad waits while untouched, from 0 at
 * the left to 1 at the right.
 * @property restY Where down its zone a floating or dynamic pad waits while untouched, from 0 at
 * the top to 1 at the bottom.
 * @property deadZone The share of [radius] near the centre that reports nothing, so a resting thumb
 * does not drift; from 0 up to, but not including, 1.
 * @property response How a deflection past the dead zone grows: in proportion, or slowly at first
 * for fine control near the centre.
 * @property curve A response drawn as points, deflection (`at`, 0 to 1) to output (`value`, 0 to
 * 1), in place of [response]; null uses [response].
 * @property directions Whether the stick reports any angle, or only the nearest of four or eight.
 * @property snapBack Whether letting go centres the knob and the deflection. False holds the last
 * deflection until the next touch, as a throttle does.
 * @property sprintThreshold How far out, as a share of [radius], the knob must be to sprint:
 * [CanvasElement.sprinting] is true and [sprintAction] is held from there to the edge. 0 never
 * sprints.
 * @property sprintAction The input action held while [CanvasElement.sprinting], such as `run`; empty
 * leaves sprinting to the game to read.
 * @property idleAlpha The opacity the Joystick fades to while nobody touches it, from 0, hidden, to
 * 1, which never fades. A touch shows it at once.
 * @property idleDelay How many seconds after a release, or after it first shows, the Joystick
 * starts to fade.
 * @property fadeSeconds How many seconds the fade to [idleAlpha] takes.
 */
@Serializable
data class CanvasJoystick(
    val mode: CanvasJoystickMode = CanvasJoystickMode.Fixed,
    @PropertyRange(min = 0.0) val radius: Float = 0f,
    @PropertyRange(min = 0.0, max = 1.0) val restX: Float = HALF,
    @PropertyRange(min = 0.0, max = 1.0) val restY: Float = HALF,
    @PropertyRange(min = 0.0, max = 1.0, exclusiveMax = true) val deadZone: Float = 0f,
    val response: CanvasStickResponse = CanvasStickResponse.Linear,
    val curve: CanvasCurve? = null,
    val directions: CanvasStickDirections = CanvasStickDirections.Analog,
    val snapBack: Boolean = true,
    @PropertyRange(min = 0.0, max = 1.0) val sprintThreshold: Float = 0f,
    val sprintAction: String = "",
    @PropertyRange(min = 0.0, max = 1.0) val idleAlpha: Float = 1f,
    @PropertyRange(min = 0.0) val idleDelay: Float = 0f,
    @PropertyRange(min = 0.0) val fadeSeconds: Float = DEFAULT_FADE_SECONDS,
) {
    /** What is wrong with these settings, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (radius < 0f) add("radius must not be negative")
        if (restX !in 0f..1f || restY !in 0f..1f) add("restX and restY must be between 0 and 1")
        if (deadZone < 0f || deadZone >= 1f) add("deadZone must be at least 0 and below 1")
        if (idleAlpha !in 0f..1f) add("idleAlpha must be between 0 and 1")
        if (idleDelay < 0f || fadeSeconds < 0f) add("idleDelay and fadeSeconds must not be negative")
        curve?.problems()?.forEach { add("curve.$it") }
        if (curve != null && response != CanvasStickResponse.Linear) add("curve and response both shape the deflection; keep one")
        addAll(sprintProblems())
    }

    private fun sprintProblems(): List<String> = listOfNotNull(
        "sprintThreshold must be between 0 and 1".takeIf { sprintThreshold !in 0f..1f },
        "sprintThreshold must be past deadZone".takeIf { sprintThreshold > 0f && sprintThreshold <= deadZone },
        "sprintAction needs a sprintThreshold above 0".takeIf { sprintAction.isNotEmpty() && sprintThreshold == 0f },
    )

    /** The knob's reach in dp in a box [width] by [height] dp. */
    fun radiusIn(width: Float, height: Float): Float = when {
        radius > 0f -> radius
        mode == CanvasJoystickMode.Fixed -> minOf(width, height) / 2f
        else -> DEFAULT_ZONE_RADIUS
    }

    /**
     * The stick's output for a knob ([dx], [dy]) dp from the pad's centre, with a reach of [reach]
     * dp, as `(x, y)` from -1 to 1, y down as on screen: the dead zone, response and directions
     * applied in that order.
     */
    fun deflection(dx: Float, dy: Float, reach: Float): Pair<Float, Float> {
        val length = sqrt(dx * dx + dy * dy)
        val share = if (reach > 0f) (length / reach).coerceAtMost(1f) else 0f
        if (length == 0f || share <= deadZone) return ZERO
        val stretched = (share - deadZone) / (1f - deadZone)
        val shaped = (curve?.valueAt(stretched, stretched) ?: response.shape(stretched)).coerceIn(0f, 1f)
        val angle = directions.snap(atan2(dy, dx))
        return Pair(cos(angle) * shaped, sin(angle) * shaped)
    }

    /** Defaults and limits. */
    companion object {
        /** A Joystick's settings when it has none: fixed, no dead zone, in proportion, any angle, never fading. */
        val Default = CanvasJoystick()

        /** A floating or dynamic pad's reach, in dp, when [radius] is 0. */
        const val DEFAULT_ZONE_RADIUS = 60f

        /** How long a fade to [idleAlpha] takes by default, in seconds. */
        const val DEFAULT_FADE_SECONDS = 0.25f

        private const val HALF = 0.5f
        private val ZERO = Pair(0f, 0f)
    }
}

/** Where a Joystick's pad sits. */
@Serializable
enum class CanvasJoystickMode {
    /** Centred in the element, where it always is. */
    Fixed,

    /** Appears where the thumb lands inside the element, and stays there until it lifts. */
    Floating,

    /** Appears where the thumb lands, and is dragged along when the thumb goes past its reach. */
    Dynamic,
}

/** How a Joystick's deflection past its dead zone grows into its output. */
@Serializable
enum class CanvasStickResponse {
    /** In proportion to the deflection. */
    Linear,

    /** As its square: slow near the centre, for fine aim, and full at the edge. */
    Quadratic,

    /** As its cube: slower still near the centre. */
    Cubic,

    ;

    internal fun shape(share: Float): Float = when (this) {
        Linear -> share
        Quadratic -> share * share
        Cubic -> share * share * share
    }
}

/** Which directions a Joystick reports. */
@Serializable
enum class CanvasStickDirections {
    /** Any angle. */
    Analog,

    /** The nearest of up, down, left and right. */
    Four,

    /** The nearest of the four and the four diagonals between them. */
    Eight,

    ;

    internal fun snap(radians: Float): Float {
        val steps = when (this) {
            Analog -> return radians
            Four -> FOUR
            Eight -> EIGHT
        }
        val step = 2f * PI.toFloat() / steps
        return (radians / step).roundToInt() * step
    }

    private companion object {
        const val FOUR = 4
        const val EIGHT = 8
    }
}
