/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.math.Vec3f

/**
 * How particles move once spawned -- everything about direction/spread/turbulence lives here,
 * grouped separately from [ParticleVisual]/[ParticleGround]/[ParticleLifecycle]/
 * [ParticleDynamics] because these are independent, freely-COMBINABLE dimensions (unlike
 * `RenderPipeline`'s `PipelineVariant`, these aren't a closed set of named shapes -- a real
 * effect wants turbulence AND a cone burst AND ground-stop together, so a sealed preset would
 * either explode combinatorially or block real combos; small grouped value classes fix the
 * param-count bloat without that trade-off).
 *
 * Every particle spawns [spawnRadius] units from [ParticleEmitter.origin], on a random point
 * around a flat horizontal ring (`0f`, the default, spawns exactly at `origin`, unchanged from
 * before this field existed).
 *
 * Velocity resolution, in priority order:
 * 1. [convergeToOrigin] `true`: direction points from the spawn point back toward `origin`, at
 *    [baseVelocity]'s own speed -- combined with [spawnRadius] > 0, this is a "charging circle"
 *    (particles appear on a ring and converge inward), the classic cast/channel-spell VFX.
 * 2. [coneHalfAngleDegrees] set (and [baseVelocity] non-zero): direction randomized within that
 *    half-angle around [baseVelocity]'s own direction, at [baseVelocity]'s own speed -- a
 *    cone/fan burst. [velocityJitter] is ignored in this mode.
 * 3. Otherwise: [baseVelocity] plus independent per-axis [velocityJitter] -- a soft cloud.
 *
 * [radialSpeed] then adds that speed horizontally away from `origin`, through the spawn point on
 * the [spawnRadius] ring, or in a random horizontal direction when [spawnRadius] is `0f`: a ring of
 * dust blown outward. It is ignored with [convergeToOrigin].
 *
 * [turbulence] adds a smooth flow-field offset to velocity every frame, scaled by this strength
 * and sampled at [turbulenceFrequency]. `0f` (default) is a no-op. Known limit: a cheap sine-based flow
 * field, not true Perlin/simplex/curl noise.
 *
 * [acceleration] is a constant world-space acceleration, in units per second squared, added to
 * every live particle's velocity each step: `Vec3f(0f, -9.8f, 0f)` is gravity. The zero default
 * is a no-op. A settled particle (see [ParticleGround]) no longer moves, so it ignores it.
 *
 * [inheritOrientation] `true` turns the spawn ring and [baseVelocity] (so also a cone's axis and
 * [velocityJitter]'s axes) by the rotation of the emitter entity, as [EmitterPlacement.orientation]
 * reports it, so an emitter turned to face a direction fires that way. Scale is ignored.
 * [acceleration], [radialSpeed] and [convergeToOrigin] stay world-space, because gravity and "away
 * from or toward the origin" mean the same thing however the emitter is turned. `false` (default)
 * keeps every vector in world axes, and an emitter entity the placement has no orientation for has
 * no rotation to inherit.
 *
 * [orientSpawnRing] `true` tilts the spawn ring alone by that same rotation, so the ring lies in the
 * plane a [ParticleFacing.Flat] quad of this emitter lies in and turns with its sprites, while
 * [baseVelocity] and every other vector stay in world axes: a magic circle laid on a sloped wall whose
 * sparks still rise straight up. Scale is ignored. It adds nothing beside [inheritOrientation], which
 * already turns the ring, or in [ParticleSpace.Local], where the ring already lies in the entity's axes.
 *
 * [radialInRingPlane] `true` makes [radialSpeed] push outward in the turned spawn ring's own plane
 * instead of horizontally, whenever [orientSpawnRing] or [inheritOrientation] turns the ring: an
 * outward ripple across a sloped surface. With the ring unturned, its plane is horizontal, so it adds
 * nothing; in [ParticleSpace.Local] the push is already in the ring's plane of the entity's axes.
 *
 * [space] is where spawned particles are kept: in the world where they were emitted
 * ([ParticleSpace.World], the default), or relative to the emitter entity so they move, turn and scale
 * with it ([ParticleSpace.Local]). See [ParticleSpace] for how each vector here reads in local space.
 */
data class ParticleMotion(
    val baseVelocity: Vec3f = Vec3f(0f, 1f, 0f),
    val velocityJitter: Float = 0f,
    val coneHalfAngleDegrees: Float? = null,
    val spawnRadius: Float = 0f,
    val convergeToOrigin: Boolean = false,
    val turbulence: Float = 0f,
    val turbulenceFrequency: Float = 1f,
    val radialSpeed: Float = 0f,
    val acceleration: Vec3f = Vec3f(0f, 0f, 0f),
    val inheritOrientation: Boolean = false,
    val space: ParticleSpace = ParticleSpace.World,
    val orientSpawnRing: Boolean = false,
    val radialInRingPlane: Boolean = false,
) {
    /** [ParticleMotion] as it was before [space]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        baseVelocity: Vec3f = Vec3f(0f, 1f, 0f),
        velocityJitter: Float = 0f,
        coneHalfAngleDegrees: Float? = null,
        spawnRadius: Float = 0f,
        convergeToOrigin: Boolean = false,
        turbulence: Float = 0f,
        turbulenceFrequency: Float = 1f,
        radialSpeed: Float = 0f,
        acceleration: Vec3f = Vec3f(0f, 0f, 0f),
        inheritOrientation: Boolean = false,
    ) : this(
        baseVelocity, velocityJitter, coneHalfAngleDegrees, spawnRadius, convergeToOrigin, turbulence,
        turbulenceFrequency, radialSpeed, acceleration, inheritOrientation, ParticleSpace.World, false, false,
    )

    /** [ParticleMotion] as it was before [orientSpawnRing]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        baseVelocity: Vec3f = Vec3f(0f, 1f, 0f),
        velocityJitter: Float = 0f,
        coneHalfAngleDegrees: Float? = null,
        spawnRadius: Float = 0f,
        convergeToOrigin: Boolean = false,
        turbulence: Float = 0f,
        turbulenceFrequency: Float = 1f,
        radialSpeed: Float = 0f,
        acceleration: Vec3f = Vec3f(0f, 0f, 0f),
        inheritOrientation: Boolean = false,
        space: ParticleSpace = ParticleSpace.World,
    ) : this(
        baseVelocity, velocityJitter, coneHalfAngleDegrees, spawnRadius, convergeToOrigin, turbulence,
        turbulenceFrequency, radialSpeed, acceleration, inheritOrientation, space, false, false,
    )

    /** [copy] as it was before [space]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        baseVelocity: Vec3f = this.baseVelocity,
        velocityJitter: Float = this.velocityJitter,
        coneHalfAngleDegrees: Float? = this.coneHalfAngleDegrees,
        spawnRadius: Float = this.spawnRadius,
        convergeToOrigin: Boolean = this.convergeToOrigin,
        turbulence: Float = this.turbulence,
        turbulenceFrequency: Float = this.turbulenceFrequency,
        radialSpeed: Float = this.radialSpeed,
        acceleration: Vec3f = this.acceleration,
        inheritOrientation: Boolean = this.inheritOrientation,
    ): ParticleMotion = ParticleMotion(
        baseVelocity, velocityJitter, coneHalfAngleDegrees, spawnRadius, convergeToOrigin, turbulence,
        turbulenceFrequency, radialSpeed, acceleration, inheritOrientation, space, orientSpawnRing,
        radialInRingPlane,
    )

    /** [copy] as it was before [orientSpawnRing]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        baseVelocity: Vec3f = this.baseVelocity,
        velocityJitter: Float = this.velocityJitter,
        coneHalfAngleDegrees: Float? = this.coneHalfAngleDegrees,
        spawnRadius: Float = this.spawnRadius,
        convergeToOrigin: Boolean = this.convergeToOrigin,
        turbulence: Float = this.turbulence,
        turbulenceFrequency: Float = this.turbulenceFrequency,
        radialSpeed: Float = this.radialSpeed,
        acceleration: Vec3f = this.acceleration,
        inheritOrientation: Boolean = this.inheritOrientation,
        space: ParticleSpace = this.space,
    ): ParticleMotion = ParticleMotion(
        baseVelocity, velocityJitter, coneHalfAngleDegrees, spawnRadius, convergeToOrigin, turbulence,
        turbulenceFrequency, radialSpeed, acceleration, inheritOrientation, space, orientSpawnRing,
        radialInRingPlane,
    )

    /** [ParticleMotion] as it was before [radialInRingPlane]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        baseVelocity: Vec3f = Vec3f(0f, 1f, 0f),
        velocityJitter: Float = 0f,
        coneHalfAngleDegrees: Float? = null,
        spawnRadius: Float = 0f,
        convergeToOrigin: Boolean = false,
        turbulence: Float = 0f,
        turbulenceFrequency: Float = 1f,
        radialSpeed: Float = 0f,
        acceleration: Vec3f = Vec3f(0f, 0f, 0f),
        inheritOrientation: Boolean = false,
        space: ParticleSpace = ParticleSpace.World,
        orientSpawnRing: Boolean = false,
    ) : this(
        baseVelocity, velocityJitter, coneHalfAngleDegrees, spawnRadius, convergeToOrigin, turbulence,
        turbulenceFrequency, radialSpeed, acceleration, inheritOrientation, space, orientSpawnRing, false,
    )

    /** [copy] as it was before [radialInRingPlane]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        baseVelocity: Vec3f = this.baseVelocity,
        velocityJitter: Float = this.velocityJitter,
        coneHalfAngleDegrees: Float? = this.coneHalfAngleDegrees,
        spawnRadius: Float = this.spawnRadius,
        convergeToOrigin: Boolean = this.convergeToOrigin,
        turbulence: Float = this.turbulence,
        turbulenceFrequency: Float = this.turbulenceFrequency,
        radialSpeed: Float = this.radialSpeed,
        acceleration: Vec3f = this.acceleration,
        inheritOrientation: Boolean = this.inheritOrientation,
        space: ParticleSpace = this.space,
        orientSpawnRing: Boolean = this.orientSpawnRing,
    ): ParticleMotion = ParticleMotion(
        baseVelocity, velocityJitter, coneHalfAngleDegrees, spawnRadius, convergeToOrigin, turbulence,
        turbulenceFrequency, radialSpeed, acceleration, inheritOrientation, space, orientSpawnRing,
        radialInRingPlane,
    )
}
