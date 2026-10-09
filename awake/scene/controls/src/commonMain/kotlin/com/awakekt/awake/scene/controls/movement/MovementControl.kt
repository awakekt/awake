/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ecs.Poolable
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign
import kotlinx.serialization.Serializable

private const val TWO_PI = (2 * PI).toFloat()

/** What sets a [MovementControl]'s intent, and so which way its axes point. */
@Serializable
enum class MovementDriver {
    /** The local player's keys and touch controls; the intent is relative to the active camera. */
    Player,

    /**
     * Code such as AI, a network or a script; the intent is a world-space direction, and player input
     * leaves it alone.
     */
    Agent,
}

/** How the player's run key runs. */
@Serializable
enum class RunMode {
    /** Runs while the key is held, and walks otherwise. */
    Hold,

    /** Starts running; each press of the key switches between walking and running. */
    Toggle,
}

/**
 * Stores intended translation deltas for a character or player.
 */
class MovementControl : Poolable {
    /** What sets this intent: the local player, relative to the camera, or code, in world space. */
    var driver: MovementDriver = MovementDriver.Player

    /** Lateral movement intent: the camera's right for a player, world X for an agent. */
    var moveX: Float = 0f

    /** Vertical movement intent along the Y axis. */
    var moveY: Float = 0f

    /** Longitudinal movement intent: the camera's forward for a player, world Z for an agent. */
    var moveZ: Float = 0f

    /** Units per second for this entity. Null uses the movement system's speed. */
    var speed: Float? = null

    /**
     * Units per second this intent asks for, set by code steering an [MovementDriver.Agent], such as
     * an AI behaviour moving at its own speed. While set it replaces [speed] and [runSpeed], which
     * stay as authored. Null leaves them in charge.
     */
    var moveSpeed: Float? = null

    /** Whether a jump is wanted this frame. A character controller decides whether it can jump. */
    var jump: Boolean = false

    /** Whether the player asks to run this frame; [runSpeed] then replaces [speed]. */
    var run: Boolean = false

    /** Units per second while [run] is held. Null keeps [speed]. */
    var runSpeed: Float? = null

    /** Whether the player holds [runKey] to run, or presses it to switch between walking and running. */
    var runMode: RunMode = RunMode.Hold

    /** The key the player runs with, as [runMode] says. */
    var runKey: Key = Key.Shift

    /** Radians per second the entity turns to face where it moves. 0 leaves its facing alone. */
    var turnSpeed: Float = 0f

    override fun reset() {
        driver = MovementDriver.Player
        moveX = 0f
        moveY = 0f
        moveZ = 0f
        speed = null
        moveSpeed = null
        jump = false
        run = false
        runSpeed = null
        runMode = RunMode.Hold
        runKey = Key.Shift
        turnSpeed = 0f
    }

    /**
     * Resolves the effective movement speed in units per second for the current frame.
     *
     * @param default Fallback speed in units per second if none of [moveSpeed], [runSpeed] and [speed] is set.
     * @return Effective movement speed in units per second.
     */
    fun currentSpeed(default: Float): Float = moveSpeed ?: (if (run) runSpeed else null) ?: speed ?: default

    /** World X of this intent: as given for an [MovementDriver.Agent], along [basis] for a player. */
    fun worldX(basis: CameraRelativeBasis): Float =
        if (driver == MovementDriver.Agent) moveX else basis.worldX(moveX, moveZ)

    /** World Z of this intent: as given for an [MovementDriver.Agent], along [basis] for a player. */
    fun worldZ(basis: CameraRelativeBasis): Float =
        if (driver == MovementDriver.Agent) moveZ else basis.worldZ(moveX, moveZ)

    /**
     * Turns [transform] toward world direction ([worldX], [worldZ]) by at most [turnSpeed] times
     * [delta] radians, the short way round. A model faces +Z at yaw 0, glTF's forward.
     *
     * @param transform Transform component whose yaw orientation is updated.
     * @param worldX Target direction X component in world space.
     * @param worldZ Target direction Z component in world space.
     * @param delta Frame time delta in seconds.
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
