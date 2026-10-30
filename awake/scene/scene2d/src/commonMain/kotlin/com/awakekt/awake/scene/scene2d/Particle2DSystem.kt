/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.atan2

private const val INITIAL_SPENT_CAPACITY_2D: Int = 8

/**
 * Advances enabled [ParticleEmitter2D] components once per frame after `TransformSystem` and before
 * `RenderSystem3D`, allocating zero heap objects in steady state.
 *
 * World-space emitters (`localSpace == false`) spawn at the entity's resolved 2D world position so
 * already-live particles trail behind a moving node; local-space emitters (`localSpace == true`)
 * simulate around `(0, 0)` and [collectSpriteDraws] transforms their quads by the entity's
 * `worldMatrix`.
 *
 * One-shot emitters whose [ParticleEmitter2D.burstCount] has finished aging out destroy their
 * entity automatically unless owned by [BurstEmitter2DPool], which recycles its resident slot by
 * setting `enabled = false`.
 *
 * @param isPlaying Whether simulation advances this frame (`false` freezes live particles in place,
 * e.g. while an editor is paused).
 */
class Particle2DSystem(private val isPlaying: () -> Boolean = { true }) : System {
    private val emitterType = ParticleEmitter2D::class
    private val transformType = Transform::class
    private var spentEntityIds = LongArray(INITIAL_SPENT_CAPACITY_2D)
    private var spentCount = 0
    private var activeEmitter: ParticleEmitter2D? = null
    private val forwardDeath: (Float, Float, Float) -> Unit = { x, y, _ ->
        activeEmitter?.onParticleDeath?.invoke(x, y)
    }

    override fun update(world: World, delta: Float) {
        if (!isPlaying()) return
        spentCount = 0
        world.queryEach(emitterType) { entity, emitter ->
            if (!emitter.enabled) return@queryEach
            stepEmitter(world, entity, emitter, delta)
        }
        val count = spentCount
        for (i in 0 until count) {
            val entity = Entity(spentEntityIds[i])
            if (world.isAlive(entity)) {
                world.destroy(entity)
            }
        }
        spentCount = 0
    }

    private fun stepEmitter(world: World, entity: Entity, emitter: ParticleEmitter2D, delta: Float) {
        val sim = emitter.simulation
        emitter.syncToSimulation()
        val transform = world.get(entity, transformType)
        if (emitter.localSpace || transform == null) {
            sim.setOrigin(0f, 0f, 0f)
            sim.inheritOrientation = false
        } else {
            val useWorldMatrix = transform.parent != null || transform.worldVersion > 0
            if (useWorldMatrix) {
                val m = transform.worldMatrix
                sim.setOrigin(m.m03, m.m13, 0f)
            } else {
                sim.setOrigin(transform.position.x, transform.position.y, 0f)
            }
            val rotateLaunch = emitter.inheritOrientation
            sim.inheritOrientation = rotateLaunch
            if (rotateLaunch) {
                val zRadians = if (useWorldMatrix) {
                    val m = transform.worldMatrix
                    atan2(m.m10, m.m00)
                } else {
                    transform.rotation.z
                }
                sim.orientation.setRotationZ(zRadians)
            }
        }

        val rateProvider = emitter.dynamicSpawnRate
        val effectiveRate = if (rateProvider != null) rateProvider() else Float.NaN
        if (emitter.onParticleDeath == null) {
            sim.step(delta, effectiveRate)
        } else {
            val previous = activeEmitter
            activeEmitter = emitter
            try {
                sim.step(delta, effectiveRate, forwardDeath)
            } finally {
                activeEmitter = previous
            }
        }

        if (sim.isComplete) {
            if (emitter.pooledForBurst) {
                emitter.enabled = false
            } else {
                recordSpentEntity(entity.packed)
            }
        }
    }

    private fun recordSpentEntity(packedId: Long) {
        if (spentCount == spentEntityIds.size) {
            spentEntityIds = spentEntityIds.copyOf(spentEntityIds.size * 2)
        }
        spentEntityIds[spentCount] = packedId
        spentCount += 1
    }
}
