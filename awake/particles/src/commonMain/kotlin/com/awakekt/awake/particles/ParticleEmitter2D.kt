/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.particles.ParticleAlphaRamp
import com.awakekt.awake.core.particles.ParticleBurstSchedule
import com.awakekt.awake.core.particles.ParticleDeathListener
import com.awakekt.awake.core.particles.ParticlePlane
import com.awakekt.awake.core.particles.ParticleSimulation
import com.awakekt.awake.core.particles.ParticleSpawnShape
import com.awakekt.awake.core.particles.ParticleSpinRange
import com.awakekt.awake.core.particles.UNLIMITED_BURST_COUNT
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val DEFAULT_POOL_CAPACITY_2D: Int = 64
private const val DEFAULT_RATE_2D: Float = 12f
private const val DEFAULT_LIFE_2D: Float = 1f
private const val DEFAULT_WORLD_SIZE_2D: Float = 0.25f
private const val DEFAULT_SHEET_FPS_2D: Float = 8f
private const val DEFAULT_DIRECTION_DEG_2D: Float = 90f
private const val DEFAULT_SPREAD_DEG_2D: Float = 360f
private const val HALF_SPREAD_SCALE_2D: Float = 0.5f
private const val DEG_TO_RAD_2D: Float = (PI / 180.0).toFloat()

/**
 * The sorting layer a 2D emitter is on unless it names another: the same name as a scene's
 * `SortingLayers.DEFAULT`, which a scene2d test keeps equal.
 */
const val DEFAULT_PARTICLE_SORTING_LAYER_2D: String = "Default"

/**
 * Asked for a 2D emitter's spawn rate, in particles per second, once a frame in place of
 * [ParticleEmitter2D.spawnRate]. A `fun interface` rather than `() -> Float`, so the rate comes back
 * as a primitive and asking allocates nothing.
 */
fun interface ParticleSpawnRate {
    /** The spawn rate for this frame, in particles per second. */
    fun particlesPerSecond(): Float
}

/**
 * A 2D particle emitter, an ECS component stepped by [Particle2DSystem] on the shared
 * [ParticleSimulation] in the `XY` plane, and turned into
 * [com.awakekt.awake.render.passes.sprites.SpriteDrawInput] quads by [appendSpriteDraws], so its
 * particles sort alongside sprites and tilemaps by [sortingLayer] and [sortOrder].
 * `awake:scene:scene2d` binds it to the `particle_emitter_2d` scene component; nothing here needs a
 * scene.
 *
 * All spatial extents, sizes, speeds, and accelerations are in 2D world units on the `XY` plane.
 * Leaving [texture] empty (`""`) draws solid-tinted 1×1 quads; naming a project sheet resolves it
 * through the scene's sprite batch with [columns] × [rows] cells and [frameCount] animated frames.
 *
 * @property maxParticles Fixed slot capacity (`>= 0`). With `0` the emitter steps but spawns
 * nothing; what [emit] and a [burstSchedule] ask for is counted in [droppedParticleCount].
 * @param random Initial [ParticleEmitter2D.random].
 */
class ParticleEmitter2D(
    val maxParticles: Int = DEFAULT_POOL_CAPACITY_2D,
    random: Random = Random.Default,
) {
    /**
     * Backing structure-of-arrays simulation pool in [ParticlePlane.XY]. Internal because
     * [syncToSimulation] rewrites its configuration from this emitter's own fields on every step and
     * [emit], so a change made on it directly would silently revert.
     */
    internal val simulation: ParticleSimulation = ParticleSimulation(maxParticles, random).also {
        it.plane = ParticlePlane.XY
    }

    /**
     * Pseudo-random source for spawn shapes, launch spread, sprite-frame offsets and spin.
     * [Random.Default] unless set; set a seeded [Random] for repeatable playback.
     */
    var random: Random
        get() = simulation.random
        set(value) {
            simulation.random = value
        }

    /** Project image path for particle quads, or `""` for untextured solid-tinted quads. */
    var texture: String = ""

    /** Sprite-sheet columns (`>= 1`). */
    var columns: Int = 1

    /** Sprite-sheet rows (`>= 1`). */
    var rows: Int = 1

    /** Animated cells cycled in reading order from `0` (`1..columns*rows`). */
    var frameCount: Int = 1

    /** Sprite-sheet playback rate in frames per second (`>= 0`). */
    var frameRate: Float = DEFAULT_SHEET_FPS_2D

    /** Whether [Particle2DSystem] steps this emitter and [appendSpriteDraws] emits its quads. */
    var enabled: Boolean = true

    /** Continuous emission rate in particles per second (`>= 0`). */
    var spawnRate: Float = DEFAULT_RATE_2D

    /** Minimum particle lifetime in seconds (`> 0`). */
    var lifetime: Float = DEFAULT_LIFE_2D

    /** Optional upper bound on random particle lifetime (`>= lifetime`). */
    var maxLifetime: Float? = null

    /** Particle quad height in world units at birth (`> 0`); width preserves cell aspect ratio. */
    var size: Float = DEFAULT_WORLD_SIZE_2D

    /** Optional particle quad height in world units interpolated to at death (`>= 0`). */
    var endSize: Float? = null

    /** Opacity at birth (`0..1`). */
    var startAlpha: Float = 1f

    /** Optional fade-in / hold / fade-out envelope over normalized lifetime. */
    var alphaRamp: ParticleAlphaRamp? = null

    /** Straight-alpha colour multiplier at birth. */
    var tint: Color = Color.White

    /** Optional straight-alpha colour multiplier interpolated to at death. */
    var endTint: Color? = null

    /** Spatial spawn distribution around the emitter's 2D origin. */
    var spawnShape: ParticleSpawnShape = ParticleSpawnShape.Point

    /** Launch speed in world units per second (`>= 0`). */
    var speed: Float = 1f

    /** Optional upper bound on random launch speed (`>= speed`). */
    var maxSpeed: Float? = null

    /** Base launch angle in degrees (`0` = +X right, `90` = +Y up). */
    var directionDegrees: Float = DEFAULT_DIRECTION_DEG_2D

    /** Fan angle in degrees around [directionDegrees] (`0` = beam, `360` = full circle). */
    var spreadDegrees: Float = DEFAULT_SPREAD_DEG_2D

    /** Outward 2D speed added away from the emitter origin. */
    var radialSpeed: Float = 0f

    /** Sends spawned particles inward toward the emitter origin at launch speed. */
    var convergeToOrigin: Boolean = false

    /** Constant X acceleration in world units per second squared. */
    var gravityX: Float = 0f

    /** Constant Y acceleration in world units per second squared (`-9.8f` pulls downward). */
    var gravityY: Float = 0f

    /** Linear velocity damping per second (`0` disables drag). */
    var drag: Float = 0f

    /** Smooth sine flow-field perturbation strength (`0` disables turbulence). */
    var turbulence: Float = 0f

    /** Spatial frequency of the turbulence flow field (`>= 0`). */
    var turbulenceFrequency: Float = 1f

    /** Optional per-particle angular velocity range in degrees per second. */
    var spin: ParticleSpinRange? = null

    /** Draws quads with additive blending on `spriteScenePipeline(buildAdditive = true)`. */
    var additive: Boolean = false

    /** Keeps live particles attached to the entity: they simulate around `(0, 0)` and draw under its world matrix, instead of trailing in world space. */
    var localSpace: Boolean = false

    /** Rotates [spawnShape] and launch velocity by the entity's Z rotation when [localSpace] is `false`. */
    var inheritOrientation: Boolean = false

    /** Sorting layer name, ordered alongside sprites and tilemaps. */
    var sortingLayer: String = DEFAULT_PARTICLE_SORTING_LAYER_2D

    /** Paint order within [sortingLayer]; higher values draw over lower ones. */
    var sortOrder: Int = 0

    /** Optional lifetime spawn cap; non-pooled entities are destroyed once all spawned particles expire. */
    var burstCount: Int? = null

    /** Optional repeating pulsed emission schedule replacing continuous [spawnRate]. */
    var burstSchedule: ParticleBurstSchedule? = null

    /**
     * Optional spawn-rate override in particles per second, asked by [Particle2DSystem] once per frame
     * in place of [spawnRate]. It is asked only on the continuous-rate path: not while a
     * [burstSchedule] replaces the rate, and not once a [burstCount] is used up.
     */
    var dynamicSpawnRate: ParticleSpawnRate? = null

    /**
     * Optional listener told where each aged-out particle dies, right before its slot is cleared.
     * [ParticleDeathListener.onDeath] receives the particle's `(x, y)` in world units (local to the
     * entity when [localSpace]) and `z = 0`. A `fun interface`, so a death allocates nothing.
     */
    var onParticleDeath: ParticleDeathListener? = null

    /** Set on resident slots owned by [BurstEmitter2DPool] so expiry disables rather than destroys them. */
    internal var pooledForBurst: Boolean = false

    /**
     * Set by [BurstEmitter2DPool] on a slot it fires: the slot's entity has no placement, so
     * [Particle2DSystem] spawns at `(pinnedX, pinnedY)` and [appendSpriteDraws] draws at `pinnedZ`,
     * both in world space whatever [localSpace] says.
     */
    internal var pinned: Boolean = false
    internal var pinnedX: Float = 0f
    internal var pinnedY: Float = 0f
    internal var pinnedZ: Float = 0f

    /** One model matrix per slot, rewritten by [appendSpriteDraws] each draw, so drawing a particle allocates no matrix. */
    internal val slotModels: Array<Mat4> = Array(maxParticles) { Mat4() }

    /** The tint [appendSpriteDraws] last drew each slot with, kept while it does not change. */
    internal val slotTints: Array<Color?> = arrayOfNulls(maxParticles)

    /** A local-space particle's own transform, before [appendSpriteDraws] puts it under its parent. */
    internal val localScratch: Mat4 = Mat4()

    /** Number of currently live particles (`0..maxParticles`). */
    val liveParticleCount: Int
        get() = simulation.liveParticleCount

    /** Cumulative count of burst/scheduled particles skipped because all [maxParticles] slots were live. */
    val droppedParticleCount: Long
        get() = simulation.droppedParticleCount

    init {
        syncToSimulation()
    }

    /**
     * Immediately emits up to [count] particles into free pool slots using the current properties and
     * [simulation] origin, without counting against [burstCount].
     *
     * Any unspawned remainder when the pool is full increments [droppedParticleCount] with zero
     * heap allocations. Returns the number actually spawned (`0..count`).
     */
    fun emit(count: Int): Int {
        syncToSimulation()
        return simulation.emit(count)
    }

    /** Clears all live particles, accumulators, and counters in [simulation]. */
    fun reset() {
        simulation.reset()
    }

    /** Restores default option values before [BurstEmitter2DPool] reconfigures a recycled slot. */
    internal fun resetDefaults() {
        texture = ""
        columns = 1
        rows = 1
        frameCount = 1
        frameRate = DEFAULT_SHEET_FPS_2D
        enabled = true
        spawnRate = 0f
        lifetime = DEFAULT_LIFE_2D
        maxLifetime = null
        size = DEFAULT_WORLD_SIZE_2D
        endSize = null
        startAlpha = 1f
        alphaRamp = null
        tint = Color.White
        endTint = null
        spawnShape = ParticleSpawnShape.Point
        speed = 1f
        maxSpeed = null
        directionDegrees = DEFAULT_DIRECTION_DEG_2D
        spreadDegrees = DEFAULT_SPREAD_DEG_2D
        radialSpeed = 0f
        convergeToOrigin = false
        gravityX = 0f
        gravityY = 0f
        drag = 0f
        turbulence = 0f
        turbulenceFrequency = 1f
        spin = null
        additive = false
        localSpace = false
        inheritOrientation = false
        sortingLayer = DEFAULT_PARTICLE_SORTING_LAYER_2D
        sortOrder = 0
        burstCount = null
        burstSchedule = null
        dynamicSpawnRate = null
        onParticleDeath = null
    }

    /**
     * Copies every option of [template] onto this emitter, sharing its immutable values (colours,
     * shapes, ramps, schedules, spin and hooks), so it allocates nothing. [maxParticles], [random],
     * the live particles and the counters stay this emitter's own.
     */
    internal fun copySettingsFrom(template: ParticleEmitter2D) {
        texture = template.texture
        columns = template.columns
        rows = template.rows
        frameCount = template.frameCount
        frameRate = template.frameRate
        enabled = template.enabled
        spawnRate = template.spawnRate
        lifetime = template.lifetime
        maxLifetime = template.maxLifetime
        size = template.size
        endSize = template.endSize
        startAlpha = template.startAlpha
        alphaRamp = template.alphaRamp
        tint = template.tint
        endTint = template.endTint
        spawnShape = template.spawnShape
        speed = template.speed
        maxSpeed = template.maxSpeed
        directionDegrees = template.directionDegrees
        spreadDegrees = template.spreadDegrees
        radialSpeed = template.radialSpeed
        convergeToOrigin = template.convergeToOrigin
        gravityX = template.gravityX
        gravityY = template.gravityY
        drag = template.drag
        turbulence = template.turbulence
        turbulenceFrequency = template.turbulenceFrequency
        spin = template.spin
        additive = template.additive
        localSpace = template.localSpace
        inheritOrientation = template.inheritOrientation
        sortingLayer = template.sortingLayer
        sortOrder = template.sortOrder
        burstCount = template.burstCount
        burstSchedule = template.burstSchedule
        dynamicSpawnRate = template.dynamicSpawnRate
        onParticleDeath = template.onParticleDeath
    }

    /** Copies scalar and config options into [simulation] with zero heap allocations. */
    internal fun syncToSimulation() {
        val sim = simulation
        sim.plane = ParticlePlane.XY
        sim.spawnRate = spawnRate
        val minLife = lifetime
        val maxLife = maxLifetime
        sim.minLifetime = minLife
        sim.maxLifetime = if (maxLife != null && maxLife > minLife) maxLife else minLife
        sim.initialAlpha = startAlpha
        sim.initialScale = size
        val targetSize = endSize
        sim.hasEndScale = targetSize != null
        sim.endScale = targetSize ?: size
        sim.frameCount = frameCount.coerceAtLeast(1)
        sim.frameRate = frameRate

        val minSpd = speed
        val maxSpd = maxSpeed
        val hasSpeedRange = maxSpd != null && maxSpd > minSpd
        val dirRadians = directionDegrees * DEG_TO_RAD_2D
        val dirScale = if (hasSpeedRange) 1f else minSpd
        sim.baseVelX = cos(dirRadians) * dirScale
        sim.baseVelY = sin(dirRadians) * dirScale
        sim.baseVelZ = 0f
        sim.minSpeed = minSpd
        sim.maxSpeed = if (hasSpeedRange && maxSpd != null) maxSpd else minSpd
        sim.velocityJitter = 0f
        val clampedSpread = spreadDegrees.coerceAtLeast(0f)
        sim.hasConeHalfAngle = clampedSpread > 0f || hasSpeedRange
        sim.coneHalfAngleDegrees = clampedSpread * HALF_SPREAD_SCALE_2D
        sim.radialSpeed = radialSpeed
        sim.convergeToOrigin = convergeToOrigin
        sim.accelX = gravityX
        sim.accelY = gravityY
        sim.accelZ = 0f
        sim.drag = drag
        sim.turbulence = turbulence
        sim.turbulenceFrequency = turbulenceFrequency
        sim.burstLimit = burstCount ?: if (pooledForBurst) 0 else UNLIMITED_BURST_COUNT

        sim.spawnShape.set(spawnShape)
        sim.alphaRamp.set(alphaRamp)
        sim.spin.set(spin)
        sim.burstSchedule.set(burstSchedule)
    }
}
