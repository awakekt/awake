/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import kotlin.math.atan2

private const val INITIAL_SPENT_CAPACITY_2D: Int = 8

/**
 * Advances enabled [ParticleEmitter2D] components once per frame, allocating nothing in steady
 * state. In a scene it runs after the transform pass and before rendering.
 *
 * It knows nothing of where an entity is: [placement] tells it, as it tells the 3D
 * [ParticleSystem]. A world-space emitter (`localSpace == false`) spawns at its entity's placed
 * `(x, y)`, so already-live particles trail behind a moving entity, and with
 * [ParticleEmitter2D.inheritOrientation] its spawns turn by the entity's rotation about `Z`. A
 * local-space emitter, or one whose entity has no placement, simulates around `(0, 0)`. A
 * [BurstEmitter2DPool] burst spawns where it was fired. `awake:scene:scene2d` supplies the
 * placement for the scene's `Transform`.
 *
 * One-shot emitters whose [ParticleEmitter2D.burstCount] has finished aging out destroy their
 * entity automatically unless owned by [BurstEmitter2DPool], which recycles its resident slot by
 * setting `enabled = false`.
 *
 * @param placement Where an emitter's entity is. See [EmitterPlacement].
 * @param isPlaying Whether simulation advances this frame (`false` freezes live particles in place,
 * e.g. while an editor is paused).
 */
class Particle2DSystem(
    private val placement: EmitterPlacement,
    private val isPlaying: () -> Boolean = { true },
) : System {
    private val emitterType = ParticleEmitter2D::class
    private val placed = Vec3f()
    private var spentEntityIds = LongArray(INITIAL_SPENT_CAPACITY_2D)
    private var spentCount = 0

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
        if (emitter.pinned) {
            sim.setOrigin(emitter.pinnedX, emitter.pinnedY, 0f)
            sim.inheritOrientation = false
        } else if (emitter.localSpace || !placement.position(world, entity, placed)) {
            sim.setOrigin(0f, 0f, 0f)
            sim.inheritOrientation = false
        } else {
            sim.setOrigin(placed.x, placed.y, 0f)
            val turned = if (emitter.inheritOrientation) placement.orientation(world, entity) else null
            sim.inheritOrientation = turned != null
            if (turned != null) {
                sim.orientation.setRotationZ(atan2(turned.m10, turned.m00))
            }
        }

        // The dynamic rate is asked only on the continuous-rate path: a burst cycle replaces the
        // rate, and an emitter whose burstCount is used up spawns nothing more.
        val continuousRate = !sim.burstSchedule.enabled && (sim.burstLimit < 0 || sim.spawnedTotal < sim.burstLimit)
        val rateProvider = if (continuousRate) emitter.dynamicSpawnRate else null
        val effectiveRate = if (rateProvider != null) rateProvider.particlesPerSecond() else Float.NaN
        sim.step(delta, effectiveRate, emitter.onParticleDeath)

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
