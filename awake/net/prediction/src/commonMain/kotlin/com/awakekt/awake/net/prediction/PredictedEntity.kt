/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import com.awakekt.awake.core.math.ErrorOffset
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.sqrt

/**
 * A predicted entity's pose with a [Correction] applied to it, for a game that keeps the pose itself
 * rather than in a character controller or ECS component: the drift a [Correction.Blend] owes, the speed
 * a [Correction.Wait] allows, and a visual glide that hides a [Correction.Shift]'s jump.
 *
 * [position] is the simulated pose, which the next step moves from and the [PredictionHistory] records.
 * Draw [drawnX], [drawnY], [drawnZ]: the same pose plus what is left of the glide.
 *
 * @property glideHalfLifeSeconds How fast a shift's jump fades from what is drawn, as a
 *   [ErrorOffset] half-life: the default 0.05 s leaves an eighth of it after 0.15 s, a few frames' slide
 *   rather than a jump. Zero shows the jump at once.
 * @property blendShare The most of each step's movement the owed drift goes into: a fifth reads as a little
 *   more or less movement, not a slide.
 */
class PredictedEntity(
    val glideHalfLifeSeconds: Float = 0.05f,
    val blendShare: Float = 0.2f,
) {
    /** The simulated pose. */
    val position: Vec3f = Vec3f(0f, 0f, 0f)

    /** What [Correction.Blend] still owes, put right as the entity moves ([blend]). */
    val owed: Vec3f = Vec3f(0f, 0f, 0f)

    /** The speed the prediction cap allows, from 1 down to 0: scale the next step's movement by it. */
    var speedScale: Float = 1f
        private set

    // What shifts left between the drawn and the simulated pose, fading.
    private val glide = ErrorOffset(glideHalfLifeSeconds)

    /** The pose to draw: [position] plus what is left of a shift's glide. */
    val drawnX: Float get() = position.x + glide.x
    val drawnY: Float get() = position.y + glide.y
    val drawnZ: Float get() = position.z + glide.z

    /**
     * Applies [correction]: moves [position] (and [history] with it) for a [Correction.Shift] or a
     * [Correction.Snap], owes a [Correction.Blend], and takes a [Correction.Wait]'s speed. A shift glides
     * on screen; a snap shows at once.
     */
    fun apply(correction: Correction, history: PredictionHistory) {
        speedScale = if (correction is Correction.Wait) correction.speedScale else 1f
        when (correction) {
            is Correction.Blend -> owed.set(correction.dx, correction.dy, correction.dz)
            is Correction.Shift -> {
                move(correction.dx, correction.dy, correction.dz, history)
                glide.add(-correction.dx, -correction.dy, -correction.dz)
                owed.set(0f, 0f, 0f)
            }
            is Correction.Snap -> {
                move(correction.x - position.x, correction.y - position.y, correction.z - position.z, history)
                glide.clear()
                owed.set(0f, 0f, 0f)
            }
            Correction.None, is Correction.Wait -> Unit
        }
    }

    /**
     * Puts part of the [owed] drift right after a step that moved the entity [walked] metres: at most
     * [blendShare] of it, so a standing entity is never slid. Moves [history] with it.
     */
    fun blend(walked: Float, history: PredictionHistory) {
        val left = sqrt(owed.x * owed.x + owed.y * owed.y + owed.z * owed.z)
        if (left <= 0f || walked <= 0f) return
        val f = (walked * blendShare / left).coerceAtMost(1f)
        val dx = owed.x * f
        val dy = owed.y * f
        val dz = owed.z * f
        move(dx, dy, dz, history)
        if (f >= 1f) owed.set(0f, 0f, 0f) else owed.set(owed.x - dx, owed.y - dy, owed.z - dz)
    }

    /** Fades the glide by [dt] seconds. */
    fun update(dt: Float) = glide.update(dt)

    private fun move(dx: Float, dy: Float, dz: Float, history: PredictionHistory) {
        position.set(position.x + dx, position.y + dy, position.z + dz)
        history.shift(dx, dy, dz)
    }
}
