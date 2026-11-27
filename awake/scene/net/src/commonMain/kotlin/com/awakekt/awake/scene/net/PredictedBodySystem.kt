/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family1System
import com.awakekt.awake.ecs.FamilySpec
import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.prediction.PredictionClock

/**
 * Zero-allocation [Family1System] that reconciles every active [PredictedBody] component each
 * fixed step without touching `Transform` directly, letting physics character controllers or rigid
 * bodies consume [PredictedBody.correction] via [PredictedBody.applier] or direct inspection.
 *
 * Excludes entities tagged with [PredictionPaused] at the ECS [FamilySpec] storage level and uses
 * monomorphic inline [runTick] iteration for zero per-frame allocations.
 *
 * @param clock Monotonic clock shared with input recording, sampled once per update without boxing.
 */
class PredictedBodySystem(
    private val clock: PredictionClock,
) : Family1System<PredictedBody>(PREDICTED_BODY, ACTIVE_SPEC) {
    private var frameNow: Double = 0.0

    override fun begin(world: World, delta: Float) {
        val now = clock.nowSeconds()
        require(now.isFinite()) { "nowSeconds must be finite, was $now." }
        frameNow = now
    }

    override fun update(world: World, delta: Float) {
        runTick(world, delta) { entity, body ->
            body.reconcileTick(world, entity, frameNow, delta)
        }
    }

    override fun process(world: World, delta: Float, entity: Entity, component: PredictedBody) {
        component.reconcileTick(world, entity, frameNow, delta)
    }

    private companion object {
        val PREDICTED_BODY = PredictedBody::class
        val ACTIVE_SPEC = FamilySpec.exclude(PredictionPaused::class)
    }
}
