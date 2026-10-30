/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.particles.ParticleAlphaRamp
import com.awakekt.awake.core.particles.ParticleBurstSchedule
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
 * Live 2D particle emitter component (`particle_emitter_2d`), stepped by [Particle2DSystem] on the
 * shared [ParticleSimulation] and collected into [com.awakekt.awake.render.passes.sprites.SpriteDrawInput]
 * quads by [collectSpriteDraws] so particles sort alongside [Sprite] and `tilemap` nodes by
 * [sortingLayer] and [sortOrder].
 *
 * All spatial extents, sizes, speeds, and accelerations are in 2D world units on the `XY` plane.
 * Leaving [texture] empty (`""`) draws solid-tinted 1×1 quads; naming a project sheet resolves it
 * through the scene's sprite batch with [columns] × [rows] cells and [frameCount] animated frames.
 *
 * @property maxParticles Fixed slot capacity of [simulation]; must be greater than `0`.
 * @param random Pseudo-random source for spawn shapes, launch spread, frame offsets, and spin.
 */
class ParticleEmitter2D(
    val maxParticles: Int = DEFAULT_POOL_CAPACITY_2D,
    random: Random = Random.Default,
) {
    /** Backing structure-of-arrays simulation pool in [ParticlePlane.XY]. */
    val simulation: ParticleSimulation = ParticleSimulation(maxParticles, random).also {
        it.plane = ParticlePlane.XY
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

    /** Whether [Particle2DSystem] steps this emitter and [collectSpriteDraws] emits its quads. */
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

    /** Keeps live particles attached to the entity's `Transform` instead of trailing in world space. */
    var localSpace: Boolean = false

    /** Rotates [spawnShape] and launch velocity by the entity's Z rotation when [localSpace] is `false`. */
    var inheritOrientation: Boolean = false

    /** Project sorting layer name, ordered alongside [Sprite] and `tilemap`. */
    var sortingLayer: String = SortingLayers.DEFAULT

    /** Paint order within [sortingLayer]; higher values draw over lower ones. */
    var sortOrder: Int = 0

    /** Optional lifetime spawn cap; non-pooled entities are destroyed once all spawned particles expire. */
    var burstCount: Int? = null

    /** Optional repeating pulsed emission schedule replacing continuous [spawnRate]. */
    var burstSchedule: ParticleBurstSchedule? = null

    /** Optional per-frame spawn-rate override evaluated by [Particle2DSystem] without allocating. */
    var dynamicSpawnRate: (() -> Float)? = null

    /** Optional callback invoked with `(x, y)` right before an aged-out particle slot is cleared. */
    var onParticleDeath: ((x: Float, y: Float) -> Unit)? = null

    /** Set on resident slots owned by [BurstEmitter2DPool] so expiry disables rather than destroys them. */
    internal var pooledForBurst: Boolean = false

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
        sortingLayer = SortingLayers.DEFAULT
        sortOrder = 0
        burstCount = null
        burstSchedule = null
        dynamicSpawnRate = null
        onParticleDeath = null
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
