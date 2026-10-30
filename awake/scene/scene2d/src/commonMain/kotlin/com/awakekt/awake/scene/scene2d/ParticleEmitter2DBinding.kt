/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.particles.ParticleAlphaRamp
import com.awakekt.awake.core.particles.ParticleBurstSchedule
import com.awakekt.awake.core.particles.ParticleSpawnShape
import com.awakekt.awake.core.particles.ParticleSpinRange
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.BurstEmitter2DPool
import com.awakekt.awake.particles.ParticleEmitter2D
import com.awakekt.awake.scene.binding.RegisteredBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneVec2
import kotlin.random.Random
import kotlin.reflect.KClass

/**
 * Constructs a live [ParticleEmitter2D] from authored [SceneParticleEmitter2D] settings.
 */
fun ParticleEmitter2D(
    settings: SceneParticleEmitter2D,
    random: Random = Random.Default,
): ParticleEmitter2D = ParticleEmitter2D(
    maxParticles = settings.maxParticles,
    random = random,
).also { it.applySceneSettings(settings) }

/**
 * A validated burst template for [BurstEmitter2DPool.spawn]: build it once from these authored
 * settings (this is where invalid settings throw, not mid-game), then fire it as often as needed;
 * each burst copies it without allocating. Its pool capacity is `0`, so it is never stepped or
 * drawn itself.
 */
fun SceneParticleEmitter2D.toBurstTemplate(): ParticleEmitter2D =
    ParticleEmitter2D(maxParticles = 0).also { it.applySceneSettings(this) }

/**
 * Copies every authored property of [settings] onto this [ParticleEmitter2D]. Validates
 * [settings] first and throws on a problem, so call it at load time, not per frame.
 */
fun ParticleEmitter2D.applySceneSettings(settings: SceneParticleEmitter2D) {
    val issues = settings.validate("particle_emitter_2d")
    require(issues.isEmpty()) { issues.joinToString("; ") { it.message } }
    texture = settings.texture
    columns = settings.columns
    rows = settings.rows
    frameCount = settings.frameCount
    frameRate = settings.frameRate
    enabled = settings.enabled
    spawnRate = settings.spawnRate
    lifetime = settings.lifetime
    maxLifetime = settings.maxLifetime
    size = settings.size
    endSize = settings.endSize
    startAlpha = settings.startAlpha
    alphaRamp = settings.alphaCurve?.let { ParticleAlphaRamp(it.fadeInEnd, it.fadeOutStart) }
    tint = Color(settings.tint.r, settings.tint.g, settings.tint.b, settings.tint.a)
    endTint = settings.endTint?.let { Color(it.r, it.g, it.b, it.a) }
    spawnShape = settings.shape.toSpawnShape()
    speed = settings.speed
    maxSpeed = settings.maxSpeed
    directionDegrees = settings.directionDegrees
    spreadDegrees = settings.spreadDegrees
    radialSpeed = settings.radialSpeed
    convergeToOrigin = settings.convergeToOrigin
    gravityX = settings.gravity.x
    gravityY = settings.gravity.y
    drag = settings.drag
    turbulence = settings.turbulence
    turbulenceFrequency = settings.turbulenceFrequency
    spin = settings.spin?.let {
        ParticleSpinRange(it.minDegreesPerSecond, it.maxDegreesPerSecond, it.randomStartAngle)
    }
    additive = settings.additive
    localSpace = settings.localSpace
    inheritOrientation = settings.inheritOrientation
    sortingLayer = settings.sortingLayer
    sortOrder = settings.sortOrder
    burstCount = settings.burstCount
    burstSchedule = settings.burstCycle?.let {
        ParticleBurstSchedule(it.cycleSeconds, it.activeSeconds, it.burstInterval, it.burstSize)
    }
}

/** Exports this live [ParticleEmitter2D] back to its [SceneParticleEmitter2D] document representation. */
fun ParticleEmitter2D.toScene(): SceneParticleEmitter2D = SceneParticleEmitter2D(
    texture = texture,
    columns = columns,
    rows = rows,
    frameCount = frameCount,
    frameRate = frameRate,
    enabled = enabled,
    maxParticles = maxParticles,
    spawnRate = spawnRate,
    lifetime = lifetime,
    maxLifetime = maxLifetime,
    size = size,
    endSize = endSize,
    startAlpha = startAlpha,
    alphaCurve = alphaRamp?.let { SceneParticleAlphaCurve2D(it.fadeInEnd, it.fadeOutStart) },
    tint = SceneColor(tint.r, tint.g, tint.b, tint.a),
    endTint = endTint?.let { SceneColor(it.r, it.g, it.b, it.a) },
    shape = spawnShape.toSceneShape2D(),
    speed = speed,
    maxSpeed = maxSpeed,
    directionDegrees = directionDegrees,
    spreadDegrees = spreadDegrees,
    radialSpeed = radialSpeed,
    convergeToOrigin = convergeToOrigin,
    gravity = SceneVec2(gravityX, gravityY),
    drag = drag,
    turbulence = turbulence,
    turbulenceFrequency = turbulenceFrequency,
    spin = spin?.let {
        SceneParticleSpin2D(it.minDegreesPerSecond, it.maxDegreesPerSecond, it.randomStartAngle)
    },
    additive = additive,
    localSpace = localSpace,
    inheritOrientation = inheritOrientation,
    sortingLayer = sortingLayer,
    sortOrder = sortOrder,
    burstCount = burstCount,
    burstCycle = burstSchedule?.let {
        SceneParticleBurstCycle2D(it.cycleSeconds, it.activeSeconds, it.burstInterval, it.burstSize)
    },
)

internal fun SceneParticleShape2D.toSpawnShape(): ParticleSpawnShape = when (kind) {
    ParticleEmissionShape2D.Point -> ParticleSpawnShape.Point
    ParticleEmissionShape2D.Disc -> if (radius <= 0f) {
        ParticleSpawnShape.Point
    } else {
        ParticleSpawnShape.RingXY(innerRadius = 0f, outerRadius = radius)
    }
    ParticleEmissionShape2D.Ring -> if (radius <= 0f) {
        ParticleSpawnShape.Point
    } else {
        ParticleSpawnShape.RingXY(innerRadius = innerRadius, outerRadius = radius)
    }
    ParticleEmissionShape2D.Box -> ParticleSpawnShape.Box(halfExtentX = halfWidth, halfExtentY = halfHeight)
    ParticleEmissionShape2D.RectEdge -> ParticleSpawnShape.RectEdge(halfWidth = halfWidth, halfHeight = halfHeight)
}

internal fun ParticleSpawnShape.toSceneShape2D(): SceneParticleShape2D = when (this) {
    ParticleSpawnShape.Point -> SceneParticleShape2D(kind = ParticleEmissionShape2D.Point)
    is ParticleSpawnShape.RingXY -> if (innerRadius == 0f) {
        SceneParticleShape2D(kind = ParticleEmissionShape2D.Disc, radius = outerRadius)
    } else {
        SceneParticleShape2D(kind = ParticleEmissionShape2D.Ring, radius = outerRadius, innerRadius = innerRadius)
    }
    is ParticleSpawnShape.RingXZ -> SceneParticleShape2D(kind = ParticleEmissionShape2D.Ring, radius = radius, innerRadius = radius)
    is ParticleSpawnShape.Box -> SceneParticleShape2D(
        kind = ParticleEmissionShape2D.Box,
        halfWidth = halfExtentX,
        halfHeight = halfExtentY,
    )
    is ParticleSpawnShape.RectEdge -> SceneParticleShape2D(
        kind = ParticleEmissionShape2D.RectEdge,
        halfWidth = halfWidth,
        halfHeight = halfHeight,
    )
}

/** Loads [SceneParticleEmitter2D] onto an entity as [ParticleEmitter2D] and exports its live state back. */
@RegisteredBinding
object ParticleEmitter2DBinding : SceneComponentBinding<ParticleEmitter2D, SceneParticleEmitter2D> {
    override val componentClass: KClass<ParticleEmitter2D> = ParticleEmitter2D::class
    override val schemaClass: KClass<SceneParticleEmitter2D> = SceneParticleEmitter2D::class
    override val serializer = SceneParticleEmitter2D.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneParticleEmitter2D,
        context: SceneResolutionContext,
    ) {
        world.add(entity, ParticleEmitter2D(component))
    }

    override fun export(
        world: World,
        entity: Entity,
        component: ParticleEmitter2D,
    ): SceneParticleEmitter2D = component.toScene()
}
