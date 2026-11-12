/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A stick or aim reading, `x` right and `y` down as on screen, each from -1 to 1. One is reused for
 * every reading of a held finger, so reading it allocates nothing.
 */
class StickVector {
    /** The horizontal part, from -1 to 1 to the right. */
    var x: Float = 0f
        private set

    /** The vertical part, from -1 to 1; up is negative, as on screen. */
    var y: Float = 0f
        private set

    /** Sets both parts. */
    fun set(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    /** Whether both parts are zero. */
    val isZero: Boolean get() = x == 0f && y == 0f
}

/**
 * The maths behind a touch stick and a drag-to-aim, with no state and no allocation: a dead zone, a
 * response curve, a snap to four or eight directions, and an aim from a drag. The canvas controls and
 * the Compose ones both call it, so the same drag reads the same on either.
 */
object TouchStickMath {
    private const val FULL_TURN = 2f * PI.toFloat()

    /**
     * How far past the dead zone a knob [length] from the centre is, from 0 to 1 over [reach]: 0 inside
     * [deadZone], then stretched so the edge of the dead zone is 0 and the edge of the reach is 1.
     */
    fun stretched(length: Float, reach: Float, deadZone: Float): Float {
        val share = if (reach > 0f) (length / reach).coerceAtMost(1f) else 0f
        return if (length == 0f || share <= deadZone) 0f else (share - deadZone) / (1f - deadZone)
    }

    /** [share], from 0 to 1, raised to [power]: 1 in proportion, 2 slow near the centre, 3 slower still. */
    fun response(share: Float, power: Int): Float {
        var shaped = share
        repeat(power - 1) { shaped *= share }
        return shaped.coerceIn(0f, 1f)
    }

    /** [radians] turned to the nearest of [steps] evenly spaced directions; any angle when [steps] is 0. */
    fun snapAngle(radians: Float, steps: Int): Float {
        if (steps <= 0) return radians
        val step = FULL_TURN / steps
        return (radians / step).roundToInt() * step
    }

    /** Writes the direction of ([dx], [dy]) snapped to [steps] directions, [magnitude] long, into [out]. */
    fun polar(dx: Float, dy: Float, magnitude: Float, steps: Int, out: StickVector) {
        if (magnitude == 0f) return out.set(0f, 0f)
        val angle = snapAngle(atan2(dy, dx), steps)
        out.set(cos(angle) * magnitude, sin(angle) * magnitude)
    }

    /**
     * The stick output for a knob ([dx], [dy]) from the pad's centre with a reach of [reach], all in
     * one unit, written into [out]: the dead zone, the response of [power] and the [steps] directions
     * applied in that order.
     */
    fun deflect(dx: Float, dy: Float, reach: Float, deadZone: Float, power: Int, steps: Int, out: StickVector) {
        val length = sqrt(dx * dx + dy * dy)
        val stretched = stretched(length, reach, deadZone)
        polar(dx, dy, if (stretched == 0f) 0f else response(stretched, power), steps, out)
    }

    /**
     * The aim for a drag of ([dx], [dy]) with a full-length reach of [reach], written into [out]: zero
     * inside [deadZone] of the reach, then a unit direction, or with [ground] as far out as the drag.
     */
    fun aim(dx: Float, dy: Float, reach: Float, deadZone: Float, ground: Boolean, out: StickVector) {
        val length = sqrt(dx * dx + dy * dy)
        val share = if (reach > 0f) length / reach else 0f
        if (length == 0f || share <= deadZone) return out.set(0f, 0f)
        val scale = if (ground) share.coerceAtMost(1f) / length else 1f / length
        out.set(dx * scale, dy * scale)
    }
}
