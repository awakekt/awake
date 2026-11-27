/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.ecs.Family2
import com.awakekt.awake.ecs.FamilySpec
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.prediction.PredictionClock
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Binds net:prediction to root scene transforms. Register in the fixed phase before movement, which
 * reads [PredictedTransform.speedScale] and calls [PredictedTransform.recordInput] after moving.
 * Run TransformSystem after movement. Snapshots must be delivered on the world thread before this.
 *
 * Entities tagged with [PredictionPaused] are skipped at the ECS [FamilySpec] storage level.
 *
 * This system moves transforms directly: use it for entities whose movement owns their transform,
 * never alongside a physics character or rigid body that owns its position (use [PredictedBody]
 * and [PredictedBodySystem] for physics-driven entities). No renderer is required.
 *
 * @param clock Monotonic clock shared with input recording, sampled once per update without boxing.
 */
class PredictionSystem(private val clock: PredictionClock) : System {
    /**
     * Compatibility constructor accepting a Kotlin `() -> Double` lambda or function reference.
     * Prefer passing a [PredictionClock] (`PredictionSystem(PredictionClock { ... })`) so sampling
     * the clock does not box a `Double` on every fixed step.
     */
    @Deprecated(
        message = "Use PredictionSystem(PredictionClock { ... }) to avoid boxing Double each step.",
        replaceWith = ReplaceWith(
            "PredictionSystem(PredictionClock(nowSeconds))",
            "com.awakekt.awake.net.prediction.PredictionClock",
        ),
    )
    constructor(nowSeconds: () -> Double) : this(PredictionClock { nowSeconds() })

    private var boundWorld: World? = null
    private var boundGeneration: Int = -1
    private var cachedFamily: Family2<Transform, PredictedTransform>? = null

    override fun update(world: World, delta: Float) {
        val now = clock.nowSeconds()
        require(now.isFinite())
        family(world).forEach { _, transform, prediction ->
            prediction.update(transform, now, delta)
        }
    }

    private fun family(world: World): Family2<Transform, PredictedTransform> {
        val existing = cachedFamily
        if (existing != null && boundWorld === world && boundGeneration == world.generation) {
            return existing
        }
        return world.family(TRANSFORM, PREDICTION, ACTIVE_SPEC).also {
            boundWorld = world
            boundGeneration = world.generation
            cachedFamily = it
        }
    }

    private companion object {
        val TRANSFORM = Transform::class
        val PREDICTION = PredictedTransform::class
        val ACTIVE_SPEC = FamilySpec.exclude(PredictionPaused::class)
    }
}
