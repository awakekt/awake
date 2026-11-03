/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

// --8<-- [start:locomotion]
/** Use the same playback mechanism with positions supplied by any movement owner. */
class LocomotionPlaybackExample(private val player: AnimationPlayer) {
    private val locomotion = LocomotionPlayer(
        LocomotionConfig(
            idle = "idle",
            walk = "walk",
            run = "run",
            jump = "jump",
            walkReferenceSpeed = 2f,
            runReferenceSpeed = 6f,
        ),
    )

    fun frame(x: Float, y: Float, z: Float, dt: Float, grounded: Boolean?): AnimationPose {
        // Select before advancing the clip. Null ground contact infers the airborne state.
        locomotion.update(player, x, y, z, dt, grounded)
        return player.update(dt)
    }
}
// --8<-- [end:locomotion]
