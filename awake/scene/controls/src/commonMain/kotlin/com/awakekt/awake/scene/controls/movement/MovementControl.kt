/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.ecs.Poolable
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign

private const val TWO_PI = (2 * PI).toFloat()

/**
 * Stores intended translation deltas for a character or player.
 */
class MovementControl : Poolable {
    var moveX: Float = 0f
    var moveY: Float = 0f
    var moveZ: Float = 0f

    /** Units per second for this entity. Null uses the movement system's speed. */
    var speed: Float? = null

    /** Whether a jump is wanted this frame. A character controller decides whether it can jump. */
    var jump: Boolean = false

    /** Whether the player asks to run this frame; [runSpeed] then replaces [speed]. */
    var run: Boolean = false

    /** Units per second while [run] is held. Null keeps [speed]. */
    var runSpeed: Float? = null

    /** Radians per second the entity turns to face where it moves. 0 leaves its facing alone. */
    var turnSpeed: Float = 0f

    override fun reset() {
        moveX = 0f
        moveY = 0f
        moveZ = 0f
        speed = null
        jump = false
        run = false
        runSpeed = null
        turnSpeed = 0f
    }

    /** Units per second to move at this frame: [runSpeed] while running, else [speed], else [default]. */
    fun currentSpeed(default: Float): Float = (if (run) runSpeed else null) ?: speed ?: default

    /**
     * Turns [transform] toward world direction ([worldX], [worldZ]) by at most [turnSpeed] times
     * [delta] radians, the short way round. A model faces +Z at yaw 0, glTF's forward.
     */
    fun turnToward(transform: Transform, worldX: Float, worldZ: Float, delta: Float) {
        if (turnSpeed <= 0f || (worldX == 0f && worldZ == 0f)) return
        val target = atan2(worldX, worldZ)
        var difference = target - transform.rotation.y
        while (difference > PI) difference -= TWO_PI
        while (difference < -PI) difference += TWO_PI
        val step = turnSpeed * delta
        transform.rotation.y = if (abs(difference) <= step) target else transform.rotation.y + sign(difference) * step
    }
}
