/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.particles.ParticleOrientationBasis
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World

/**
 * Spawns/advances every [ParticleEmitter]'s particle pool -- kept separate from the draw side
 * ([ParticleDrawBuilder], which culls, sorts and packs the live particles into draw packets) so this
 * stays a single-responsibility simulation step. [ParticleDrawBuilder] reads the pool's current live
 * particles fresh each frame; this system owns writing to it.
 *
 * Also owns [ParticleEmitter.lifecycle]'s `burstCount` cleanup: a one-shot emitter's entity is
 * destroyed once every particle it will ever spawn has died, so [spawnParticleBurst] callers never
 * need their own bookkeeping. That removes the whole entity, so an emitter with a `burstCount` belongs
 * on an entity of its own.
 *
 * It knows nothing of where an entity is. [placement] tells it, for an emitter that follows an entity
 * and for one that turns its spawns by the entity's rotation. Pass [EmitterPlacement.None] to opt
 * out explicitly when an emitter should not follow or turn with its entity.
 *
 * @param placement Where an emitter's entity is. See [EmitterPlacement].
 */
class ParticleSystem(private val placement: EmitterPlacement) : System {
    private val emitterType = ParticleEmitter::class
    private val spentEntities = ArrayList<Entity>()
    private val sharedOrientation = ParticleOrientationBasis()
    private val deathPosition = Vec3f(0f, 0f, 0f)
    private var activeWorld: World? = null
    private var activeEmitter: ParticleEmitter? = null
    private val forwardDeath: (Float, Float, Float) -> Unit = { x, y, z ->
        deathPosition.set(x, y, z)
        val currentWorld = activeWorld
        val currentEmitter = activeEmitter
        if (currentWorld != null && currentEmitter != null) {
            currentEmitter.lifecycle.onParticleDeath?.invoke(currentWorld, deathPosition)
        }
    }

    override fun update(world: World, delta: Float) {
        spentEntities.clear()
        world.queryEach(emitterType) { entity, emitter ->
            followOrigin(world, emitter)
            simulate(world, entity, emitter, delta)
        }
        val spentCount = spentEntities.size
        for (i in 0 until spentCount) {
            val entity = spentEntities[i]
            // Only ever return an emitter that actually came from BurstEmitterPool.obtain -- a
            // hand-attached emitter that happens to also set burstCount is destroyed normally,
            // never pooled (see ParticleEmitter.pooledForBurst's own doc comment).
            val emitter = world.get<ParticleEmitter>(entity)
            if (emitter != null && emitter.pooledForBurst) BurstEmitterPool.release(emitter)
            world.destroy(entity)
        }
    }

    /** Advances [emitter] itself, then every entry in [ParticleEmitter.children] -- each child's
     * `origin` is recomposed as `emitter.origin + child.localOffset` first (see [ParticleEmitter
     * .children]'s own doc comment), so a child rides along with its parent's current position
     * every frame without needing its own placement/[Entity]. Exhaustion is only ever checked
     * against [isTopLevel] -- a child's own burst still caps ITS spawning, but a child finishing
     * its burst must never destroy the shared entity out from under a still-emitting parent (or
     * sibling), so only the top-level emitter's exhaustion queues the entity for destruction. */
    private fun simulate(
        world: World,
        entity: Entity,
        emitter: ParticleEmitter,
        delta: Float,
        isTopLevel: Boolean = true,
    ) {
        if (isTopLevel) sharedOrientation.setFromMatrix(placement.orientation(world, entity))
        emitter.syncToSimulation()
        emitter.simulation.orientation.copyFrom(sharedOrientation)
        val rateProvider = emitter.dynamics.dynamicSpawnRate
        val effectiveRate = if (rateProvider != null) rateProvider() else Float.NaN
        val deathHook = emitter.lifecycle.onParticleDeath
        if (deathHook == null) {
            emitter.simulation.step(delta, effectiveRate)
        } else {
            val previousWorld = activeWorld
            val previousEmitter = activeEmitter
            activeWorld = world
            activeEmitter = emitter
            try {
                emitter.simulation.step(delta, effectiveRate, forwardDeath)
            } finally {
                activeWorld = previousWorld
                activeEmitter = previousEmitter
            }
        }
        val children = emitter.children
        val childCount = children.size
        for (i in 0 until childCount) {
            val child = children[i]
            child.origin.set(
                emitter.origin.x + child.localOffset.x,
                emitter.origin.y + child.localOffset.y,
                emitter.origin.z + child.localOffset.z,
            )
            simulate(world, entity, child, delta, isTopLevel = false)
        }
        if (isTopLevel && emitter.simulation.isComplete) spentEntities += entity
    }

    /** Re-anchors [ParticleEmitter.origin] to [ParticleEmitter.dynamics]' `followEntity`'s
     * world position, if set, as [placement] reports it -- the lightweight "sub-emitter"
     * trailing-effect path (see [ParticleDynamics]'s own doc comment). A no-op when [placement]
     * has no position for the followed entity (already destroyed, or never placed) -- the emitter
     * just keeps spawning from wherever [ParticleEmitter.origin] last was, rather than crashing. */
    private fun followOrigin(world: World, emitter: ParticleEmitter) {
        val target = emitter.dynamics.followEntity ?: return
        placement.position(world, target, emitter.origin)
    }
}
