/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.particles

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.particles.ParticleAlphaCurve
import com.awakekt.awake.particles.ParticleBurstCycle
import com.awakekt.awake.particles.ParticleGround
import com.awakekt.awake.particles.ParticleLifecycle
import com.awakekt.awake.particles.ParticleMotion
import com.awakekt.awake.particles.ParticleVisual
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneVec3

// Where a scene file's `particle_emitter` becomes the live emitter's settings. It is the only place the
// two meet, so an option added to either side is wrong until it is handled here: ParticleEmitterMappingTest
// fails when a runtime option is neither mapped from the scene file nor listed there as set from code only.

/** The motion options of [this] scene file as a live [ParticleMotion]. */
internal fun SceneParticleEmitter.toMotion(): ParticleMotion = ParticleMotion(
    baseVelocity = velocity.toVec3f(),
    velocityJitter = velocityJitter,
    coneHalfAngleDegrees = coneHalfAngleDegrees,
    spawnRadius = spawnRadius,
    convergeToOrigin = convergeToOrigin,
    turbulence = turbulence,
    turbulenceFrequency = turbulenceFrequency,
    radialSpeed = radialSpeed,
    acceleration = acceleration.toVec3f(),
    inheritOrientation = inheritOrientation,
)

/** The look options of [this] scene file as a live [ParticleVisual]. */
internal fun SceneParticleEmitter.toVisual(): ParticleVisual = ParticleVisual(
    startColor = color.toVec3f(),
    endColor = (endColor ?: color).toVec3f(),
    frameCount = frameCount,
    frameRate = frameRate,
    stretchWithVelocity = stretchWithVelocity,
    stretchFactor = stretchFactor,
    endScale = endScale,
    additive = additive,
    facing = facing,
    alphaCurve = alphaCurve?.let { ParticleAlphaCurve(it.fadeInEnd, it.fadeOutStart) },
)

/** The ground options of [this] scene file as a live [ParticleGround]; no ground when it has none. */
internal fun SceneParticleEmitter.toGround(): ParticleGround = ground?.let {
    ParticleGround(
        groundY = it.groundY,
        colliders = it.colliders.map { box -> Aabb(box.min.toVec3f(), box.max.toVec3f()) },
        restitution = it.restitution,
        friction = it.friction,
    )
} ?: ParticleGround()

/** The spawn schedule options of [this] scene file as a live [ParticleLifecycle]. */
internal fun SceneParticleEmitter.toLifecycle(): ParticleLifecycle = ParticleLifecycle(
    burstCount = burstCount,
    burstCycle = burstCycle?.let { ParticleBurstCycle(it.cycleSeconds, it.activeSeconds, it.burstInterval, it.burstSize) },
)

private fun SceneVec3.toVec3f() = Vec3f(x, y, z)

private fun SceneColor.toVec3f() = Vec3f(r, g, b)
