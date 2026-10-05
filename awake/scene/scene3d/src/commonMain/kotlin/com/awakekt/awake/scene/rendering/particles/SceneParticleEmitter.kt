/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.particles

import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// This file is the scene-document schema of `particle_emitter` and nothing else: the data a scene file
// can hold and the checks on it. What a scene file becomes at run time is elsewhere: ParticleEmitterBinding
// attaches it, ParticleContentSystem builds the live ParticleEmitter from it through ParticleEmitterMapping,
// and loadParticleSprites reads its images. None of that belongs here, and nothing here needs a renderer.

/**
 * Sprites spawned at the node's world position: a [ParticleEmitter] authored as data. Sizes, speeds
 * and [spawnRadius] are world units; the node's scale does not apply. The values [facing] takes in a
 * scene file are the names of the [ParticleFacing] constants, so those names are part of the file
 * format.
 *
 * @property texture A project image file the particles show.
 * @property maxParticles The pool size; spawning pauses while every slot is live. Above 0.
 * @property spawnRate Particles per second. Not negative. [burstCycle] replaces it when set.
 * @property lifetime Seconds each particle lives. Above 0.
 * @property startAlpha Opacity at birth, 0 to 1. A particle fades from it to 0 over its [lifetime],
 * or as [alphaCurve] says.
 * @property scale Size at birth. Above 0.
 * @property endScale Size at death, grown or shrunk linearly from [scale]. Left out, it stays [scale].
 * @property velocity The starting velocity, in world units per second.
 * @property velocityJitter Random per-axis variation added to [velocity]. Ignored with
 * [coneHalfAngleDegrees].
 * @property coneHalfAngleDegrees Spreads the direction within this angle of [velocity], keeping its
 * speed.
 * @property spawnRadius Spawns on a horizontal ring of this radius around the node. Not negative.
 * @property radialSpeed Adds this speed horizontally away from the node, through the spawn point.
 * @property color The tint at birth.
 * @property endColor The tint at death, moved to linearly. Left out, it stays [color].
 * @property frameCount Treats the texture as a horizontal strip of this many equal frames. At least 1.
 * @property frameRate Frames per second of that strip. Not negative.
 * @property additive Whether particles add to what is behind them instead of blending over it, for
 * glows. It needs the plan's particle pipeline built with `buildAdditive`.
 * @property facing Turns each sprite to the camera, or with [ParticleFacing.Flat] lays it in the plane
 * perpendicular to the node's up axis, which is also how [inheritOrientation] uses the node's rotation.
 * @property acceleration A constant world-space acceleration, in units per second squared, added to
 * every live particle's velocity: `(0, -9.8, 0)` is gravity. Zero changes nothing.
 * @property inheritOrientation Whether the node's rotation turns the spawn ring and [velocity] (so
 * also the [coneHalfAngleDegrees] axis and the [velocityJitter] axes), so an emitter turned to face a
 * direction fires that way. [acceleration] and [radialSpeed] stay in world space, because gravity and
 * "away from the node" mean the same however it is turned. Scale is ignored.
 * @property alphaCurve How a particle fades in and out over its life. Left out, it fades linearly from
 * [startAlpha] to 0.
 * @property burstCycle A pulsing emission that replaces the continuous [spawnRate]. Left out, the
 * emitter spawns continuously.
 * @property turbulence How strongly a smooth flow field pushes every particle's velocity each second.
 * Zero turns it off.
 * @property turbulenceFrequency How finely that flow field varies in space and time: higher is busier.
 * Not negative.
 * @property convergeToOrigin Whether particles head toward the node, at the speed of [velocity],
 * instead of in the direction of [velocity]. With a [spawnRadius] they appear on a ring and close in,
 * for a charge-up. [radialSpeed] is ignored.
 * @property stretchWithVelocity Whether each sprite is stretched along its own motion on screen, for
 * rain, sparks and streaks, instead of staying a plain square.
 * @property stretchFactor World units of stretch per unit of speed when [stretchWithVelocity] is on.
 * Not negative.
 * @property burstCount How many particles the emitter spawns in its whole life, after which it stops.
 * Once every one of them has died **the node carrying the emitter is removed from the world**, so a
 * one-shot effect cleans itself up; put a one-shot emitter on a node of its own. Left out, it spawns
 * for as long as the scene runs.
 * @property ground Where falling particles land, and what they do there. Left out, they never land
 * and just fade out.
 */
@Serializable
@SerialName("particle_emitter")
data class SceneParticleEmitter(
    val texture: String,
    val maxParticles: Int = 64,
    val spawnRate: Float = 10f,
    val lifetime: Float = 1f,
    val startAlpha: Float = 1f,
    val scale: Float = 0.2f,
    val endScale: Float? = null,
    val velocity: SceneVec3 = SceneVec3(0f, 1f, 0f),
    val velocityJitter: Float = 0f,
    val coneHalfAngleDegrees: Float? = null,
    val spawnRadius: Float = 0f,
    val radialSpeed: Float = 0f,
    val color: SceneColor = SceneColor(),
    val endColor: SceneColor? = null,
    val frameCount: Int = 1,
    val frameRate: Float = 8f,
    val additive: Boolean = false,
    val facing: ParticleFacing = ParticleFacing.Camera,
    val acceleration: SceneVec3 = SceneVec3(0f, 0f, 0f),
    val inheritOrientation: Boolean = false,
    val alphaCurve: SceneParticleAlphaCurve? = null,
    val burstCycle: SceneParticleBurstCycle? = null,
    val turbulence: Float = 0f,
    val turbulenceFrequency: Float = 1f,
    val convergeToOrigin: Boolean = false,
    val stretchWithVelocity: Boolean = false,
    val stretchFactor: Float = 0.05f,
    val burstCount: Int? = null,
    val ground: SceneParticleGround? = null,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> =
        (basicProblems() + motionProblems() + shapeProblems()).map { SceneValidationIssue(path, "particle_emitter.$it") }

    private fun basicProblems(): List<String> = buildList {
        if (texture.isBlank()) add("texture must name an image")
        if (maxParticles <= 0) add("maxParticles must be greater than 0")
        if (spawnRate < 0f) add("spawnRate must not be negative")
        if (lifetime <= 0f) add("lifetime must be greater than 0")
        if (startAlpha !in 0f..1f) add("startAlpha must be within 0..1")
        if (scale <= 0f) add("scale must be greater than 0")
        if (endScale != null && endScale < 0f) add("endScale must not be negative")
        if (spawnRadius < 0f) add("spawnRadius must not be negative")
        if (frameCount < 1) add("frameCount must be at least 1")
        if (frameRate < 0f) add("frameRate must not be negative")
    }

    private fun motionProblems(): List<String> = buildList {
        if (!(acceleration.x.isFinite() && acceleration.y.isFinite() && acceleration.z.isFinite())) add("acceleration must be finite")
        if (!turbulence.isFinite()) add("turbulence must be finite")
        if (!(turbulenceFrequency >= 0f && turbulenceFrequency.isFinite())) add("turbulenceFrequency must be finite and not negative")
        if (!(stretchFactor >= 0f && stretchFactor.isFinite())) add("stretchFactor must be finite and not negative")
        if (burstCount != null && burstCount <= 0) add("burstCount must be greater than 0")
    }

    private fun shapeProblems(): List<String> =
        alphaCurve?.problems().orEmpty() + burstCycle?.problems().orEmpty() + ground?.problems().orEmpty()
}

/**
 * A particle's opacity over its life, as fractions of its lifetime: it fades in from 0 until
 * [fadeInEnd], holds the emitter's `startAlpha` until [fadeOutStart], then fades out to 0 at the end
 * of its life. `fadeInEnd` 0.2 with `fadeOutStart` 0.7 fades in over the first fifth and out over the
 * last 30%. Both are within 0..1 and [fadeInEnd] does not come after [fadeOutStart].
 *
 * @property fadeInEnd The fraction of its life at which a particle reaches full opacity.
 * @property fadeOutStart The fraction of its life at which it starts to fade out.
 */
@Serializable
data class SceneParticleAlphaCurve(
    val fadeInEnd: Float = 0f,
    val fadeOutStart: Float = 0f,
) {
    internal fun problems(): List<String> = buildList {
        if (fadeInEnd !in 0f..1f || fadeOutStart !in 0f..1f) add("alphaCurve fractions must be within 0..1")
        if (fadeInEnd > fadeOutStart) add("alphaCurve.fadeInEnd must not come after fadeOutStart")
    }
}

/**
 * A looping emission schedule: every [cycleSeconds], for the first [activeSeconds], spawn
 * [burstSize] particles every [burstInterval] seconds, then pause until the cycle repeats.
 * `cycleSeconds` 2, `activeSeconds` 1, `burstInterval` 0.25 and `burstSize` 5 fires 5 particles at
 * 0, 0.25, 0.5 and 0.75 seconds of each 2-second cycle and nothing in the second half. A burst that
 * finds the pool full spawns only what fits.
 *
 * @property cycleSeconds The length of one cycle. Above 0.
 * @property activeSeconds How much of the cycle bursts fire in, from its start. Above 0 and at most
 * [cycleSeconds].
 * @property burstInterval Seconds between bursts while the cycle is active. Above 0.
 * @property burstSize Particles in each burst. Above 0.
 */
@Serializable
data class SceneParticleBurstCycle(
    val cycleSeconds: Float,
    val activeSeconds: Float,
    val burstInterval: Float,
    val burstSize: Int,
) {
    internal fun problems(): List<String> = buildList {
        if (!(cycleSeconds > 0f && cycleSeconds.isFinite())) add("burstCycle.cycleSeconds must be greater than 0")
        if (!(activeSeconds > 0f && activeSeconds <= cycleSeconds)) add("burstCycle.activeSeconds must be within 0 and cycleSeconds")
        if (!(burstInterval > 0f && burstInterval.isFinite())) add("burstCycle.burstInterval must be greater than 0")
        if (burstSize <= 0) add("burstCycle.burstSize must be greater than 0")
    }
}

/**
 * Where a [SceneParticleEmitter]'s falling particles land. A particle over one of the [colliders]
 * lands on its top face; otherwise on the flat plane at [groundY]; with neither it never lands. What
 * happens on landing is [restitution]: at 0 the particle stops for good, above 0 it bounces, and
 * [friction] slows its sideways motion on every bounce. A particle that bounces back up slower than
 * 0.3 units a second settles instead of hopping forever.
 *
 * @property groundY Height of the flat ground plane. Left out, there is no plane.
 * @property restitution How much of its fall speed a landing particle keeps as it bounces back up: 0
 * stops it, 1 is a lossless bounce, above 1 gains energy. Not negative.
 * @property friction What fraction of its sideways speed a bouncing particle keeps on each bounce: 1
 * keeps it all, 0 stops it sliding. Not negative.
 * @property colliders World-space boxes whose top faces particles land on, for the ledges and crates a
 * flat plane cannot describe.
 */
@Serializable
data class SceneParticleGround(
    val groundY: Float? = null,
    val restitution: Float = 0f,
    val friction: Float = 1f,
    val colliders: List<SceneParticleBox> = emptyList(),
) {
    internal fun problems(): List<String> = buildList {
        if (groundY != null && !groundY.isFinite()) add("ground.groundY must be finite")
        if (!(restitution >= 0f && restitution.isFinite())) add("ground.restitution must be finite and not negative")
        if (!(friction >= 0f && friction.isFinite())) add("ground.friction must be finite and not negative")
        colliders.forEachIndexed { index, box ->
            if (!box.isOrdered()) add("ground.colliders[$index] must be finite with min at or below max on every axis")
        }
    }
}

/**
 * An axis-aligned box in world space, from [min] to [max] on every axis.
 *
 * @property min The corner with the smallest x, y and z.
 * @property max The corner with the largest x, y and z.
 */
@Serializable
data class SceneParticleBox(
    val min: SceneVec3,
    val max: SceneVec3,
) {
    internal fun isOrdered(): Boolean =
        listOf(min.x, min.y, min.z, max.x, max.y, max.z).all { it.isFinite() } && min.x <= max.x && min.y <= max.y && min.z <= max.z
}
