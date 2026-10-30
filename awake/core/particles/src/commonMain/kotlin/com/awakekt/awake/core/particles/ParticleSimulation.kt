/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.particles

import kotlin.random.Random

/** Sentinel value for [ParticleSimulation.burstLimit] indicating continuous, uncapped lifetime emission. */
const val UNLIMITED_BURST_COUNT: Int = -1

/**
 * Pure structure-of-arrays particle simulation shared by 3D world emitters (`awake:particles`),
 * 2D scene emitters (`awake:scene:scene2d`), and canvas UI emitters (`awake:ui:canvas`).
 *
 * All particle arrays are sized once at construction to [maxParticles]. Both [step] and [emit]
 * mutate primitive arrays in place and allocate zero heap objects on the steady-state or burst
 * path.
 *
 * @property maxParticles Fixed slot capacity of this simulation pool; must be greater than `0`.
 * @property random Pseudo-random source used for spawn positions, cone/jitter velocities, sprite
 * frame offsets, and spin rates. Supply a seeded [Random] for deterministic playback.
 */
class ParticleSimulation(
    val maxParticles: Int,
    var random: Random = Random.Default,
) {
    init {
        require(maxParticles > 0) { "maxParticles must be positive, got $maxParticles" }
    }

    /** Per-slot liveness flag (`true` while slot `i` holds an active particle). */
    val alive: BooleanArray = BooleanArray(maxParticles)

    /** Per-slot X coordinate in the caller's simulation space (world units or canvas `dp`). */
    val posX: FloatArray = FloatArray(maxParticles)

    /** Per-slot Y coordinate in the caller's simulation space (world units or canvas `dp`). */
    val posY: FloatArray = FloatArray(maxParticles)

    /** Per-slot Z coordinate in the caller's simulation space (`0` in [ParticlePlane.XY]). */
    val posZ: FloatArray = FloatArray(maxParticles)

    /** Per-slot X velocity in simulation units per second. */
    val velX: FloatArray = FloatArray(maxParticles)

    /** Per-slot Y velocity in simulation units per second. */
    val velY: FloatArray = FloatArray(maxParticles)

    /** Per-slot Z velocity in simulation units per second. */
    val velZ: FloatArray = FloatArray(maxParticles)

    /** Per-slot elapsed age in seconds since spawn. */
    val age: FloatArray = FloatArray(maxParticles)

    /** Per-slot total lifetime in seconds (`age >= lifetime` retires the slot). */
    val lifetime: FloatArray = FloatArray(maxParticles)

    /** Per-slot initial opacity (`0..1`) captured at spawn. */
    val startAlpha: FloatArray = FloatArray(maxParticles)

    /** Per-slot initial size captured at spawn. */
    val scale: FloatArray = FloatArray(maxParticles) { 1f }

    /** Per-slot flag set once a falling particle settles on [ground] and stops moving/turning. */
    val settled: BooleanArray = BooleanArray(maxParticles)

    /** Per-slot random starting sprite-frame offset in `0 until frameCount`. */
    val frameOffset: IntArray = IntArray(maxParticles)

    /** Per-slot rotation angle in radians, counter-clockwise in the quad's plane. */
    val rotation: FloatArray = FloatArray(maxParticles)

    /** Per-slot angular velocity in radians per second. */
    val spinRate: FloatArray = FloatArray(maxParticles)

    /** Number of currently live particles (`0..maxParticles`), maintained in `O(1)`. */
    var liveParticleCount: Int = 0
        internal set

    /** Total scheduled particles spawned over this simulation's life (compared against [burstLimit]). */
    var spawnedTotal: Int = 0
        internal set

    /** Total bursts triggered so far by [burstSchedule]. */
    var burstsFired: Int = 0
        internal set

    /** Total seconds accumulated across [step] calls since construction or [reset]. */
    var elapsedTime: Float = 0f
        internal set

    /**
     * Cumulative count of particles requested via [emit] or [burstSchedule] that were skipped
     * because all [maxParticles] slots were already live.
     */
    var droppedParticleCount: Long = 0L
        internal set

    internal var spawnAccumulator: Float = 0f

    /** Primary simulation plane (`XZ` for 3D world emitters, `XY` for 2D scenes and canvas UI). */
    var plane: ParticlePlane = ParticlePlane.XZ

    /** Emitter spawn origin X coordinate. */
    var originX: Float = 0f

    /** Emitter spawn origin Y coordinate. */
    var originY: Float = 0f

    /** Emitter spawn origin Z coordinate. */
    var originZ: Float = 0f

    /** Continuous emission rate in particles per second when [burstSchedule] is inactive. */
    var spawnRate: Float = 0f

    /** Minimum lifetime in seconds assigned to newly spawned particles. */
    var minLifetime: Float = 1f

    /** Maximum lifetime in seconds (`<= minLifetime` uses [minLifetime] with zero RNG calls). */
    var maxLifetime: Float = 1f

    /** Opacity (`0..1`) assigned at birth to newly spawned particles. */
    var initialAlpha: Float = 1f

    /** Size assigned at birth to newly spawned particles. */
    var initialScale: Float = 1f

    /** Whether particles interpolate size from [scale] at birth to [endScale] at death. */
    var hasEndScale: Boolean = false

    /** Target size at death when [hasEndScale] is `true`. */
    var endScale: Float = 1f

    /** Number of sprite-sheet frames (`>= 1`). */
    var frameCount: Int = 1

    /** Sprite-sheet playback rate in frames per second. */
    var frameRate: Float = 8f

    /** Base launch velocity X component. */
    var baseVelX: Float = 0f

    /** Base launch velocity Y component. */
    var baseVelY: Float = 1f

    /** Base launch velocity Z component. */
    var baseVelZ: Float = 0f

    /** Optional minimum launch speed override when `maxSpeed > minSpeed` or `minSpeed > 0f`. */
    var minSpeed: Float = 0f

    /** Optional maximum launch speed override (`<= minSpeed` avoids drawing a random speed). */
    var maxSpeed: Float = 0f

    /** Per-axis random velocity jitter amplitude when [hasConeHalfAngle] is `false`. */
    var velocityJitter: Float = 0f

    /** Whether [coneHalfAngleDegrees] shapes launch directions around the base velocity. */
    var hasConeHalfAngle: Boolean = false

    /** Cone/fan half-angle in degrees when [hasConeHalfAngle] is `true`. */
    var coneHalfAngleDegrees: Float = 0f

    /** Whether spawned particles fly inward toward `(originX, originY, originZ)` at launch speed. */
    var convergeToOrigin: Boolean = false

    /** Outward planar velocity added away from the origin in [plane]. */
    var radialSpeed: Float = 0f

    /** Whether [orientation] rotates local spawn offsets and launch velocities. */
    var inheritOrientation: Boolean = false

    /** Constant acceleration X component in units per second squared. */
    var accelX: Float = 0f

    /** Constant acceleration Y component in units per second squared. */
    var accelY: Float = 0f

    /** Constant acceleration Z component in units per second squared. */
    var accelZ: Float = 0f

    /** Linear velocity damping per second (`0f` disables drag). */
    var drag: Float = 0f

    /** Sine flow-field perturbation strength (`0f` disables turbulence). */
    var turbulence: Float = 0f

    /** Spatial frequency of the flow field used when [turbulence] is non-zero. */
    var turbulenceFrequency: Float = 1f

    /**
     * Lifetime cap on scheduled spawns ([UNLIMITED_BURST_COUNT] for continuous emission). Once
     * [spawnedTotal] reaches a non-negative limit and [liveParticleCount] returns to `0`,
     * [isComplete] becomes `true`.
     */
    var burstLimit: Int = UNLIMITED_BURST_COUNT

    /** Orthonormal basis used when [inheritOrientation] is `true`. */
    val orientation: ParticleOrientationBasis = ParticleOrientationBasis()

    /** Spatial spawn distribution around `(originX, originY, originZ)`. */
    val spawnShape: ParticleSpawnConfig = ParticleSpawnConfig()

    /** Optional fade-in / hold / fade-out opacity envelope over normalized lifetime. */
    val alphaRamp: ParticleAlphaConfig = ParticleAlphaConfig()

    /** Optional per-particle angular velocity range. */
    val spin: ParticleSpinConfig = ParticleSpinConfig()

    /** Optional repeating pulsed-burst schedule. */
    val burstSchedule: ParticleBurstConfig = ParticleBurstConfig()

    /** Optional ground plane / height provider / AABB landing surfaces. */
    val ground: ParticleGroundConfig = ParticleGroundConfig()

    /**
     * `true` when [burstLimit] is non-negative, [spawnedTotal] has reached it, and every live
     * particle has finished aging out.
     */
    val isComplete: Boolean
        get() = burstLimit >= 0 && spawnedTotal >= burstLimit && liveParticleCount == 0

    /** Sets `(originX, originY, originZ)` in one call. */
    fun setOrigin(x: Float, y: Float, z: Float = 0f) {
        originX = x
        originY = y
        originZ = z
    }

    /**
     * Advances emitter clocks, scheduled emission, and all live particles by [deltaSeconds].
     *
     * @param deltaSeconds Frame time step in seconds (`<= 0` leaves particles unchanged after
     * processing any zero-time initial burst only when `deltaSeconds > 0`).
     * @param effectiveSpawnRate Optional per-frame rate override (`Float.NaN` uses [spawnRate]).
     * @param onParticleDeath Optional callback invoked with `(x, y, z)` immediately before a
     * particle whose age reached its lifetime is cleared.
     */
    fun step(
        deltaSeconds: Float,
        effectiveSpawnRate: Float = Float.NaN,
        onParticleDeath: ((x: Float, y: Float, z: Float) -> Unit)? = null,
    ) {
        elapsedTime += deltaSeconds
        spawnScheduledSlots(this, deltaSeconds, if (effectiveSpawnRate.isNaN()) spawnRate else effectiveSpawnRate)
        advanceSimulationSlots(this, deltaSeconds, onParticleDeath)
    }

    /**
     * Immediately emits up to [count] particles into free pool slots using the current origin,
     * [spawnShape], launch velocity, lifetime, and spin settings, without counting against
     * [burstLimit].
     *
     * If fewer than [count] free slots remain, fills the remaining free slots, adds the unspawned
     * remainder to [droppedParticleCount], and returns the number actually spawned (`0..count`).
     */
    fun emit(count: Int): Int {
        if (count <= 0) return 0
        var spawned = 0
        var searchFrom = 0
        while (spawned < count) {
            val slot = findFreeSimulationSlot(this, searchFrom)
            if (slot < 0) {
                droppedParticleCount += (count - spawned).toLong()
                break
            }
            spawnConfiguredSlot(this, slot)
            spawned += 1
            searchFrom = slot + 1
        }
        return spawned
    }

    /**
     * Spawns a single particle manually at `(x, y, z)` with velocity `(vx, vy, vz)` if a free slot
     * exists, returning `true` when spawned and `false` when all [maxParticles] slots are live.
     */
    fun spawn(
        x: Float,
        y: Float,
        z: Float = 0f,
        vx: Float = 0f,
        vy: Float = 0f,
        vz: Float = 0f,
    ): Boolean {
        val slot = findFreeSimulationSlot(this, 0)
        if (slot < 0) return false
        posX[slot] = x
        posY[slot] = y
        posZ[slot] = z
        velX[slot] = vx
        velY[slot] = vy
        velZ[slot] = vz
        launchSlotMetadata(this, slot, minLifetime)
        return true
    }

    /** Retires [slot] and zeroes its per-particle state without resetting emitter clocks or counters. */
    fun clearSlot(slot: Int) {
        resetSlot(this, slot)
    }

    /** Clears all live particles, accumulators, and counters back to an unused pool state. */
    fun reset() {
        for (slot in 0 until maxParticles) {
            resetSlot(this, slot)
        }
        liveParticleCount = 0
        spawnAccumulator = 0f
        spawnedTotal = 0
        burstsFired = 0
        elapsedTime = 0f
        droppedParticleCount = 0L
    }

    /** Current opacity (`0..1`) of [slot] at its current [age], using [alphaRamp] when enabled. */
    fun alphaAt(slot: Int): Float {
        val life = lifetime[slot]
        val fraction = if (life > 0f) (age[slot] / life).coerceIn(0f, 1f) else 1f
        return if (alphaRamp.enabled) {
            startAlpha[slot] * alphaRampFactorAt(fraction, alphaRamp.fadeInEnd, alphaRamp.fadeOutStart)
        } else {
            startAlpha[slot] * (1f - fraction)
        }
    }

    /** Current size of [slot] at its current [age], interpolating toward [endScale] when [hasEndScale] is `true`. */
    fun scaleAt(slot: Int): Float {
        val base = scale[slot]
        if (!hasEndScale) return base
        val life = lifetime[slot]
        val fraction = if (life > 0f) (age[slot] / life).coerceIn(0f, 1f) else 1f
        return base + (endScale - base) * fraction
    }

    /** Current sprite-sheet frame index (`0 until frameCount`) of [slot] at its current [age]. */
    fun frameAt(slot: Int): Int {
        val total = frameCount.coerceAtLeast(1)
        val elapsedFrames = (age[slot] * frameRate).toInt()
        return (elapsedFrames + frameOffset[slot]) % total
    }
}
