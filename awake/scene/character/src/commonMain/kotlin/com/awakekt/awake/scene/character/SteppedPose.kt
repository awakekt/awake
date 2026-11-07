/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.PI

/**
 * A character's last two stepped poses, which [CharacterControllerSystem] blends between rendered frames:
 * position and yaw always, and pitch (`Transform.rotation.x`) only once game code has asked for one, so a
 * walking character's pitch is never written.
 */
internal class SteppedPose {
    private val previousPosition = Vec3f()
    private val currentPosition = Vec3f()
    private var previousYaw = 0f
    private var currentYaw = 0f
    private var blendedYaw = Float.NaN
    private var previousPitch = 0f
    private var currentPitch = 0f
    private var blendedPitch = Float.NaN
    private var stepped = false

    // Whether pitch is recorded and blended: true once game code has asked for one, never before.
    private var pitchDriven = false
    private var pitchStepped = false

    // The yaw and pitch game code asked for, applied at the start of the next step; NaN when none.
    private var facingYaw = Float.NaN
    private var facingPitch = Float.NaN

    var yaw: Float
        get() = if (facingYaw.isNaN()) currentYaw else facingYaw
        set(value) {
            facingYaw = value
        }

    var pitch: Float
        get() = if (facingPitch.isNaN()) currentPitch else facingPitch
        set(value) {
            facingPitch = value
            pitchDriven = true
        }

    /** Starts the next blend at [position], with no motion from where it was. */
    fun jumpTo(position: Vec3f) {
        previousPosition.set(position)
        currentPosition.set(position)
        previousYaw = currentYaw
        previousPitch = currentPitch
    }

    /**
     * Puts back the yaw and pitch a frame's blend left in [transform], unless something else has turned
     * it since, then applies the facing game code asked for, if any.
     */
    fun restore(transform: Transform) {
        val rotation = transform.rotation
        if (stepped && rotation.y == blendedYaw) rotation.y = currentYaw
        if (pitchStepped && rotation.x == blendedPitch) rotation.x = currentPitch
        if (!facingYaw.isNaN()) {
            rotation.y = facingYaw
            facingYaw = Float.NaN
        }
        if (!facingPitch.isNaN()) {
            // The first pitch asked for blends from the pitch the character had, not from 0.
            if (!pitchStepped) currentPitch = rotation.x
            pitchStepped = true
            rotation.x = facingPitch
            facingPitch = Float.NaN
        }
    }

    /** Keeps the pose a step left in [transform] as the newest of the two the frames blend between. */
    fun record(transform: Transform) {
        if (stepped) {
            previousPosition.set(currentPosition)
            previousYaw = currentYaw
        } else {
            previousPosition.set(transform.position)
            previousYaw = transform.rotation.y
        }
        currentPosition.set(transform.position)
        currentYaw = transform.rotation.y
        if (pitchDriven) {
            previousPitch = if (pitchStepped) currentPitch else transform.rotation.x
            currentPitch = transform.rotation.x
            pitchStepped = true
        }
        stepped = true
    }

    /**
     * Shows the pose [alpha] of the way between steps, each angle turning the short way. Invalid blend
     * fractions leave the pose alone; a non-finite stepped angle leaves that rendered angle alone while
     * the rest still blends.
     */
    fun blendInto(transform: Transform, alpha: Float) {
        val validBlend = stepped && alpha.isFinite() && alpha in 0f..1f
        if (!validBlend) return
        transform.position.set(
            previousPosition.x + (currentPosition.x - previousPosition.x) * alpha,
            previousPosition.y + (currentPosition.y - previousPosition.y) * alpha,
            previousPosition.z + (currentPosition.z - previousPosition.z) * alpha,
        )
        blendedYaw = shortWay(previousYaw, currentYaw, alpha)
        if (!blendedYaw.isNaN()) transform.rotation.y = blendedYaw
        if (pitchDriven && pitchStepped) {
            blendedPitch = shortWay(previousPitch, currentPitch, alpha)
            if (!blendedPitch.isNaN()) transform.rotation.x = blendedPitch
        }
    }

    private companion object {
        const val TWO_PI = 2 * PI

        /** [alpha] of the way from [previous] to [current] the short way round, reduced by whole turns; NaN when either isn't finite. */
        fun shortWay(previous: Float, current: Float, alpha: Float): Float {
            if (!current.isFinite() || !previous.isFinite()) return Float.NaN
            val from = previous.toDouble() % TWO_PI
            var turn = (current.toDouble() % TWO_PI - from) % TWO_PI
            if (turn > PI) turn -= TWO_PI
            if (turn < -PI) turn += TWO_PI
            return (from + turn * alpha).toFloat()
        }
    }
}
