/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Binds net:prediction to root scene transforms. Register in the fixed phase before movement, which
 * reads [PredictedTransform.speedScale] and calls [PredictedTransform.recordInput] after moving.
 * Run TransformSystem after movement. Snapshots must be delivered on the world thread before this.
 *
 * This system moves transforms directly: use it for entities whose movement owns their transform,
 * never alongside a physics character or rigid body that owns its position. No renderer is required.
 *
 * @param nowSeconds Monotonic clock shared with input recording, sampled once per update.
 */
class PredictionSystem(private val nowSeconds: () -> Double) : System {
    override fun update(world: World, delta: Float) {
        val now = nowSeconds()
        require(now.isFinite())
        world.queryEach(TRANSFORM, PREDICTION) { _, transform, prediction ->
            prediction.update(transform, now, delta)
        }
    }

    private companion object {
        val TRANSFORM = Transform::class
        val PREDICTION = PredictedTransform::class
    }
}
