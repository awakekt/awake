/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.camera

import com.awakekt.awake.core.math.Vec3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The pose math of a camera that looks by yaw and pitch, orbits a pivot, zooms, pans and flies, on
 * plain numbers and vectors, with no scene or ECS dependency. Angles are in radians: yaw 0 faces -Z,
 * a positive yaw turns toward +X, and a negative pitch looks down.
 *
 * Every function writes into the `out` vector it is given and allocates nothing, so a camera system
 * calls them every frame. None clamps or rejects what it is given beyond what it says; unlike
 * `CameraMathUtils` in `awake:core:math`, which clamps pitch to 89 degrees, rejects a non-finite or
 * zero distance and allocates in `computeOrbitEye`, so it is the tool for an editor's validated pose
 * rather than a game camera's frame.
 */
object CameraPose {
    /** The steepest a look turns up or down by [turnedPitch], 85 degrees. */
    const val PITCH_LIMIT: Float = (85.0 * PI / 180.0).toFloat()

    /** Writes into [out] the unit direction a camera at [yaw] and [pitch] looks along. */
    fun forward(yaw: Float, pitch: Float, out: Vec3f): Vec3f {
        val cp = cos(pitch)
        return out.set(sin(yaw) * cp, sin(pitch), -cos(yaw) * cp)
    }

    /** Writes into [out] the eye [distance] back from [pivot] along the view at [yaw] and [pitch]. */
    fun orbitEye(pivot: Vec3f, yaw: Float, pitch: Float, distance: Float, out: Vec3f): Vec3f {
        val cp = cos(pitch)
        return out.set(
            pivot.x - sin(yaw) * cp * distance,
            pivot.y - sin(pitch) * distance,
            pivot.z - -cos(yaw) * cp * distance,
        )
    }

    /** [yaw] turned by a pointer moved [dx] pixels, at [sensitivity] radians a pixel. */
    fun turnedYaw(yaw: Float, dx: Float, sensitivity: Float, invert: Boolean = false): Float =
        yaw + dx * sensitivity * (if (invert) -1f else 1f)

    /** [pitch] turned by a pointer moved [dy] pixels down, kept within [PITCH_LIMIT] either way. */
    fun turnedPitch(pitch: Float, dy: Float, sensitivity: Float, invert: Boolean = false): Float =
        (pitch - dy * sensitivity * (if (invert) -1f else 1f)).coerceIn(-PITCH_LIMIT, PITCH_LIMIT)

    /**
     * [distance] after [scrollDeltaY] notches of the wheel, each [sensitivity] plus [proportion] of
     * the distance, so zoom keeps pace far out; a positive scroll comes closer.
     */
    fun scrolledDistance(distance: Float, scrollDeltaY: Float, sensitivity: Float, proportion: Float = 0f): Float =
        distance - scrollDeltaY * (sensitivity + distance * proportion)

    /** [distance] after a pinch of [scale]: spreading the fingers 1.5 times comes 1.5 times closer. */
    fun pinchedDistance(distance: Float, scale: Float): Float = distance / scale

    /**
     * Writes into [right] and [up] the axes of the view from [eye] to [center], with [right] level,
     * so a pan moves in the plane the camera shows. [scratch] holds the view direction.
     */
    fun viewAxes(eye: Vec3f, center: Vec3f, right: Vec3f, up: Vec3f, scratch: Vec3f) {
        val forward = scratch.set(center).sub(eye).normalize()
        right.set(-forward.z, 0f, forward.x).normalize()
        up.set(
            right.y * forward.z - right.z * forward.y,
            right.z * forward.x - right.x * forward.z,
            right.x * forward.y - right.y * forward.x,
        )
    }

    /**
     * Writes into [out] how far a pointer dragged [dx] and [dy] pixels moves what it drags: along
     * [up] and against [right], [scale] world units a pixel. [right] and [up] are left scaled.
     */
    fun panShift(right: Vec3f, up: Vec3f, dx: Float, dy: Float, scale: Float, out: Vec3f): Vec3f =
        out.set(up.scale(dy * scale)).add(right.scale(-dx * scale))

    /**
     * Writes into [out] the unit direction a free-flying camera at [yaw] and [pitch] moves, [ahead]
     * along its view, [across] to its level right and [rise] straight up; zero when all three are.
     */
    fun flyDirection(yaw: Float, pitch: Float, ahead: Float, across: Float, rise: Float, out: Vec3f): Vec3f {
        val cp = cos(pitch)
        return out.set(
            sin(yaw) * cp * ahead + cos(yaw) * across + 0f,
            sin(pitch) * ahead + 0f * across + rise,
            -cos(yaw) * cp * ahead + sin(yaw) * across + 0f,
        ).normalize()
    }
}
