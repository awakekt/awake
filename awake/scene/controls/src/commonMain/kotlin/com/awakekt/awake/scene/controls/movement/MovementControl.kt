/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.ecs.Poolable

/**
 * Stores intended translation deltas for a character or player.
 */
class MovementControl : Poolable {
    var moveX: Float = 0f
    var moveY: Float = 0f
    var moveZ: Float = 0f

    /** Units per second for this entity. Null uses the movement system's speed. */
    var speed: Float? = null

    /** Whether a jump is wanted this frame. [JumpSystem] acts on it only while grounded. */
    var jump: Boolean = false

    internal var verticalVelocity: Float = 0f

    /** Height the entity lands on, taken from where it first stood. NaN until then. */
    internal var restY: Float = Float.NaN

    override fun reset() {
        moveX = 0f
        moveY = 0f
        moveZ = 0f
        speed = null
        jump = false
        verticalVelocity = 0f
        restY = Float.NaN
    }
}
