/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.random.Random

private const val DEFAULT_BURST_SLOTS_2D: Int = 32
private const val DEFAULT_BURST_SLOT_PARTICLES_2D: Int = 64

/**
 * Fixed-capacity resident pool of one-shot [ParticleEmitter2D] entities for hit sparks, pickups,
 * footsteps, and 2D combat bursts with zero per-burst ECS structural churn or heap allocations.
 *
 * Calling [prepare] (or the first [spawn]) creates [maxSlots] disabled entities in the target
 * [World], each holding a pre-allocated [Transform] and [ParticleEmitter2D] of capacity
 * [slotMaxParticles]. Each [spawn] activates a free resident slot in place, sets its world `(x, y, z)`
 * position, runs [ParticleEmitter2D.emit], and arms automatic recycling (`enabled = false`) once
 * the burst's particles finish aging out in [Particle2DSystem].
 *
 * **Overflow policy (skip and count):** when all [maxSlots] slots are currently active, [spawn]
 * skips the burst without evicting an in-flight effect or allocating a new entity, increments
 * [droppedBurstCount], and returns `false`.
 *
 * @property maxSlots Number of resident burst entities pre-allocated in the world (`> 0`).
 * @property slotMaxParticles Particle pool capacity of each resident slot (`> 0`).
 * @param randomFactory Supplies the [Random] instance for each slot constructed during [prepare].
 */
class BurstEmitter2DPool(
    val maxSlots: Int = DEFAULT_BURST_SLOTS_2D,
    val slotMaxParticles: Int = DEFAULT_BURST_SLOT_PARTICLES_2D,
    private val randomFactory: (slotIndex: Int) -> Random = { Random.Default },
) {
    init {
        require(maxSlots > 0) { "maxSlots must be positive, got $maxSlots" }
        require(slotMaxParticles > 0) { "slotMaxParticles must be positive, got $slotMaxParticles" }
    }

    private val slotEntityIds = LongArray(maxSlots)
    private val slotTransforms = arrayOfNulls<Transform>(maxSlots)
    private val slotEmitters = arrayOfNulls<ParticleEmitter2D>(maxSlots)
    private var preparedWorld: World? = null
    private var nextCursor: Int = 0

    /** Cumulative count of [spawn] calls skipped because all [maxSlots] resident slots were active. */
    var droppedBurstCount: Long = 0L
        private set

    /** Number of resident slots currently active (`enabled && !simulation.isComplete`). */
    val activeSlotCount: Int
        get() {
            val world = preparedWorld ?: return 0
            var active = 0
            for (i in 0 until maxSlots) {
                if (isSlotActive(world, i)) {
                    active += 1
                }
            }
            return active
        }

    /**
     * Pre-allocates all [maxSlots] resident disabled burst entities in [world] if not already
     * prepared for [world]. Safe to call repeatedly on the same [world].
     */
    fun prepare(world: World) {
        if (preparedWorld === world && allSlotsAlive(world)) return
        if (preparedWorld != null && preparedWorld !== world) {
            clearReferences()
        }
        preparedWorld = world
        nextCursor = 0
        for (i in 0 until maxSlots) {
            val entity = world.create()
            val transform = Transform()
            val emitter = ParticleEmitter2D(maxParticles = slotMaxParticles, random = randomFactory(i)).apply {
                enabled = false
                pooledForBurst = true
            }
            world.add(entity, transform)
            world.add(entity, emitter)
            slotEntityIds[i] = entity.packed
            slotTransforms[i] = transform
            slotEmitters[i] = emitter
        }
    }

    /**
     * Fires a one-shot burst of [count] particles at `(x, y, z)` in [world] using [configure] to
     * customize the recycled [ParticleEmitter2D] in place with zero heap allocations.
     *
     * Returns `true` when a free slot was acquired, or `false` (incrementing [droppedBurstCount])
     * when all [maxSlots] slots are currently active.
     */
    fun spawn(
        world: World,
        x: Float,
        y: Float,
        count: Int = slotMaxParticles,
        z: Float = 0f,
        configure: (ParticleEmitter2D) -> Unit = {},
    ): Boolean {
        prepare(world)
        val slot = acquireFreeSlot(world)
        if (slot < 0) {
            droppedBurstCount += 1L
            return false
        }
        val emitter = slotEmitters[slot]
        emitter?.resetDefaults()
        if (emitter != null) configure(emitter)
        return launchSlot(slot, x, y, z, count)
    }

    /**
     * Fires a one-shot burst at `(x, y, z)` in [world] configured from authored [settings].
     *
     * Returns `true` when a free slot was acquired, or `false` (incrementing [droppedBurstCount])
     * when all [maxSlots] slots are currently active.
     */
    fun spawn(
        world: World,
        x: Float,
        y: Float,
        settings: SceneParticleEmitter2D,
        count: Int = settings.burstCount ?: slotMaxParticles,
        z: Float = 0f,
    ): Boolean {
        prepare(world)
        val slot = acquireFreeSlot(world)
        if (slot < 0) {
            droppedBurstCount += 1L
            return false
        }
        val emitter = slotEmitters[slot]
        emitter?.resetDefaults()
        emitter?.applySceneSettings(settings)
        return launchSlot(slot, x, y, z, count)
    }

    /** Destroys all resident entities owned by this pool in [world] and resets pool references. */
    fun dispose(world: World) {
        if (preparedWorld === world) {
            for (i in 0 until maxSlots) {
                val entity = Entity(slotEntityIds[i])
                if (world.isAlive(entity)) {
                    world.destroy(entity)
                }
            }
        }
        clearReferences()
    }

    private fun launchSlot(
        slot: Int,
        x: Float,
        y: Float,
        z: Float,
        count: Int,
    ): Boolean {
        val transform = slotTransforms[slot]
        val emitter = slotEmitters[slot]
        if (transform == null || emitter == null) return false

        transform.position.set(x, y, z)
        transform.rotation.set(0f, 0f, 0f)
        transform.scale.set(1f, 1f, 1f)
        transform.computeLocalMatrix()

        val sim = emitter.simulation
        sim.reset()
        emitter.spawnRate = 0f
        emitter.burstCount = null
        emitter.burstSchedule = null
        emitter.syncToSimulation()
        if (emitter.localSpace) {
            sim.setOrigin(0f, 0f, 0f)
            sim.inheritOrientation = false
        } else {
            sim.setOrigin(x, y, 0f)
        }
        sim.burstLimit = 0
        val spawned = sim.emit(count)
        emitter.enabled = spawned > 0
        return true
    }

    private fun acquireFreeSlot(world: World): Int {
        for (offset in 0 until maxSlots) {
            val idx = (nextCursor + offset) % maxSlots
            if (isSlotFree(world, idx)) {
                nextCursor = (idx + 1) % maxSlots
                return idx
            }
        }
        return -1
    }

    private fun isSlotActive(world: World, slot: Int): Boolean {
        val emitter = slotEmitters[slot] ?: return false
        return world.isAlive(Entity(slotEntityIds[slot])) && emitter.enabled && !emitter.simulation.isComplete
    }

    private fun isSlotFree(world: World, slot: Int): Boolean {
        val emitter = slotEmitters[slot] ?: return false
        return world.isAlive(Entity(slotEntityIds[slot])) && (!emitter.enabled || emitter.simulation.isComplete)
    }

    private fun allSlotsAlive(world: World): Boolean {
        for (i in 0 until maxSlots) {
            if (slotEmitters[i] == null || !world.isAlive(Entity(slotEntityIds[i]))) return false
        }
        return true
    }

    private fun clearReferences() {
        slotEntityIds.fill(0L)
        slotTransforms.fill(null)
        slotEmitters.fill(null)
        preparedWorld = null
        nextCursor = 0
    }
}
