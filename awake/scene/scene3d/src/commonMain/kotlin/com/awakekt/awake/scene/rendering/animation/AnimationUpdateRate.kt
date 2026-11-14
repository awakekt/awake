/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.animation.AnimationPose

/**
 * Opt-in policy deciding how often [AnimationSystem] recomputes a drawn skinned model's pose, by the
 * model's distance from the camera: a far model, whose joints barely move across a handful of screen
 * pixels, is posed less often than one up close. The clip's clock still advances every frame, so
 * playback timing is unchanged; only the joint palette the renderer reads is held between recomputes,
 * which is where the per-frame cost ([AnimationPose.jointPalette]) is.
 *
 * Off by default: [AnimationSystem] uses [EveryFrame] unless a consuming app passes a rate, and the
 * app authors the distances — Core ships the mechanism, not the tuning. The engine neither measures
 * distance nor throttles when the default is in force.
 *
 * A recommended starting band, for a world in metres seen by a third-person camera, is
 * `byDistance(near = 12f, far = 40f, farInterval = 0.1f)`: full rate up close, easing to ten poses a
 * second at 40 m, where a character spans a few dozen pixels. Widen `near` for a camera that frames
 * characters closely; shorten `farInterval` if far crowds visibly step:
 *
 * ```kotlin
 * val animation = AnimationSystem(AnimationUpdateRate.byDistance(near = 12f, far = 40f, farInterval = 0.1f))
 * ```
 *
 * A model whose pose is held (speed 0, a finished clip on its last frame) is not posed again at any
 * rate; see [com.awakekt.awake.core.animation.AnimationPlayer.updateIfChanged].
 */
fun interface AnimationUpdateRate {
    /**
     * Seconds to hold a model's pose between recomputes when it is [cameraDistance] world units from
     * the camera; `0` (or less) poses it every frame. Called once per drawn skinned model per frame,
     * so it must not allocate.
     */
    fun intervalFor(cameraDistance: Float): Float

    /** Ready-made update rates: the default [EveryFrame] and the distance band [byDistance] builds. */
    companion object {
        /** Poses every drawn model every frame: the engine's unchanged default behavior. */
        val EveryFrame: AnimationUpdateRate = AnimationUpdateRate { 0f }

        /**
         * Poses every frame within [near] world units of the camera, holds a pose for [farInterval]
         * seconds beyond [far], and eases linearly between the two across the band in between, so a
         * model does not jump from full-rate to throttled as it crosses one threshold.
         *
         * @param near Distance within which a model is posed every frame; `0 <= near <= far`.
         * @param far Distance beyond which a model is posed every [farInterval] seconds.
         * @param farInterval Seconds between poses at and beyond [far]; must not be negative.
         */
        fun byDistance(near: Float, far: Float, farInterval: Float): AnimationUpdateRate {
            require(near >= 0f && far >= near) { "AnimationUpdateRate.byDistance needs 0 <= near <= far." }
            require(farInterval >= 0f) { "AnimationUpdateRate.byDistance needs farInterval >= 0." }
            val span = far - near
            return AnimationUpdateRate { distance ->
                when {
                    distance <= near -> 0f
                    distance >= far || span <= 0f -> farInterval
                    else -> farInterval * ((distance - near) / span)
                }
            }
        }
    }
}
