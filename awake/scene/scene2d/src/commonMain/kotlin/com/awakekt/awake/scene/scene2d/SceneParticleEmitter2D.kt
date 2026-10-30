/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DEFAULT_MAX_PARTICLES_2D: Int = 64
private const val DEFAULT_SPAWN_RATE_2D: Float = 12f
private const val DEFAULT_LIFETIME_2D: Float = 1f
private const val DEFAULT_SIZE_2D: Float = 0.25f
private const val DEFAULT_FRAME_RATE_2D: Float = 8f
private const val DEFAULT_DIRECTION_DEGREES_2D: Float = 90f
private const val FULL_CIRCLE_DEGREES_2D: Float = 360f

/**
 * 2D spatial spawn distribution kind for [SceneParticleShape2D].
 */
@Serializable
enum class ParticleEmissionShape2D {
    /** Spawns directly at the emitter's origin. */
    Point,

    /** Spawns uniformly by area inside a filled circle of [SceneParticleShape2D.radius]. */
    Disc,

    /**
     * Spawns uniformly inside an annulus between [SceneParticleShape2D.innerRadius] and
     * [SceneParticleShape2D.radius] (circle perimeter when equal).
     */
    Ring,

    /**
     * Spawns uniformly inside a rectangle `[-halfWidth..halfWidth, -halfHeight..halfHeight]`.
     */
    Box,

    /**
     * Spawns uniformly by arc length along the perimeter of `[-halfWidth..halfWidth, -halfHeight..halfHeight]`.
     */
    RectEdge,
}

/**
 * Spatial spawn region around a [SceneParticleEmitter2D]'s origin in 2D world units.
 *
 * @property kind Distribution shape (`Point`, `Disc`, `Ring`, `Box`, or `RectEdge`).
 * @property radius Outer circle radius for `Disc` and `Ring`, in world units. Not negative.
 * @property innerRadius Inner hole radius for `Ring`, in world units; at most [radius]. Not negative.
 * @property halfWidth Half-width along local X for `Box` and `RectEdge`, in world units. Not negative.
 * @property halfHeight Half-height along local Y for `Box` and `RectEdge`, in world units. Not negative.
 */
@Serializable
data class SceneParticleShape2D(
    val kind: ParticleEmissionShape2D = ParticleEmissionShape2D.Point,
    @PropertyRange(min = 0.0) val radius: Float = 0f,
    @PropertyRange(min = 0.0) val innerRadius: Float = 0f,
    @PropertyRange(min = 0.0) val halfWidth: Float = 0f,
    @PropertyRange(min = 0.0) val halfHeight: Float = 0f,
) {
    internal fun problems(): List<String> = buildList {
        if (!radius.isNonNegativeFinite()) add("shape.radius must be finite and not negative")
        if (!innerRadius.isNonNegativeFinite()) add("shape.innerRadius must be finite and not negative")
        if (!halfWidth.isNonNegativeFinite()) add("shape.halfWidth must be finite and not negative")
        if (!halfHeight.isNonNegativeFinite()) add("shape.halfHeight must be finite and not negative")
        val validRadii = radius.isFinite() && innerRadius.isFinite()
        if (kind == ParticleEmissionShape2D.Ring && validRadii && innerRadius > radius) {
            add("shape.innerRadius must not exceed radius")
        }
    }
}

/**
 * Opacity ramp over a 2D particle's normalized lifetime (`0..1`): fades in linearly from `0` until
 * [fadeInEnd], holds `startAlpha` until [fadeOutStart], then fades out linearly to `0` at `1`.
 *
 * @property fadeInEnd Normalized age (`0..1`) where opacity reaches `startAlpha`.
 * @property fadeOutStart Normalized age (`0..1`) where opacity begins fading toward `0`.
 */
@Serializable
data class SceneParticleAlphaCurve2D(
    val fadeInEnd: Float = 0f,
    val fadeOutStart: Float = 0f,
) {
    internal fun problems(): List<String> = buildList {
        if (fadeInEnd !in 0f..1f || fadeOutStart !in 0f..1f) add("alphaCurve fractions must be within 0..1")
        if (fadeInEnd > fadeOutStart) add("alphaCurve.fadeInEnd must not come after fadeOutStart")
    }
}

/**
 * Per-particle angular velocity range for a [SceneParticleEmitter2D] in the 2D XY plane.
 *
 * @property minDegreesPerSecond Minimum counter-clockwise spin rate in degrees per second.
 * @property maxDegreesPerSecond Maximum counter-clockwise spin rate in degrees per second.
 * @property randomStartAngle Whether each particle also starts at a uniform random angle.
 */
@Serializable
data class SceneParticleSpin2D(
    val minDegreesPerSecond: Float,
    val maxDegreesPerSecond: Float,
    val randomStartAngle: Boolean = false,
) {
    internal fun problems(): List<String> = buildList {
        if (!(minDegreesPerSecond.isFinite() && maxDegreesPerSecond.isFinite())) {
            add("spin rates must be finite")
        } else if (minDegreesPerSecond > maxDegreesPerSecond) {
            add("spin.minDegreesPerSecond must not be above maxDegreesPerSecond")
        }
    }
}

/**
 * Looping pulsed emission schedule for a [SceneParticleEmitter2D], replacing continuous `spawnRate`.
 *
 * @property cycleSeconds Duration of one full cycle in seconds. Above 0.
 * @property activeSeconds Active window from the start of each cycle in seconds (`(0, cycleSeconds]`).
 * @property burstInterval Seconds between bursts inside the active window. Above 0.
 * @property burstSize Number of particles requested per burst. Above 0.
 */
@Serializable
data class SceneParticleBurstCycle2D(
    val cycleSeconds: Float,
    val activeSeconds: Float,
    val burstInterval: Float,
    val burstSize: Int,
) {
    internal fun problems(): List<String> = buildList {
        if (!(cycleSeconds > 0f && cycleSeconds.isFinite())) add("burstCycle.cycleSeconds must be greater than 0")
        if (!(activeSeconds > 0f && activeSeconds <= cycleSeconds)) {
            add("burstCycle.activeSeconds must be within 0 and cycleSeconds")
        }
        if (!(burstInterval > 0f && burstInterval.isFinite())) add("burstCycle.burstInterval must be greater than 0")
        if (burstSize <= 0) add("burstCycle.burstSize must be greater than 0")
    }
}

/**
 * Authored 2D particle emitter (`particle_emitter_2d`) drawn through the unlit sprite pipeline and
 * sorted alongside `sprite` and `tilemap` nodes by [sortingLayer] and [sortOrder].
 *
 * Sizes, speeds, and [shape] extents are 2D world units, backed by `awake:core:particles`'s shared
 * `ParticleSimulation`. Leaving [texture] empty (`""`) draws solid-tinted quads; naming a project
 * image resolves it through the scene's sprite batch with optional [columns] × [rows] frame sheets.
 *
 * @property texture Project image path for particle quads, or `""` for untextured solid-tinted quads.
 * @property columns Sprite-sheet columns when [texture] is non-empty. At least 1.
 * @property rows Sprite-sheet rows when [texture] is non-empty. At least 1.
 * @property frameCount Animated cells used in reading order from `0` (`1..columns*rows`). At least 1.
 * @property frameRate Playback rate in frames per second across [frameCount]. Not negative.
 * @property enabled Whether this emitter steps and draws.
 * @property maxParticles Fixed pool capacity; spawning pauses while every slot is live. Above 0.
 * @property spawnRate Continuous emission rate in particles per second. Not negative.
 * @property lifetime Minimum particle lifetime in seconds. Above 0.
 * @property maxLifetime Optional upper bound on random particle lifetime (`>= lifetime`). Above 0.
 * @property size Particle height in world units at birth (width preserves cell aspect). Above 0.
 * @property endSize Optional particle height in world units at death. Not negative.
 * @property startAlpha Opacity at birth, from 0 to 1.
 * @property alphaCurve Optional fade-in / hold / fade-out envelope over normalized lifetime.
 * @property tint Straight-alpha colour multiplier at birth.
 * @property endTint Optional colour multiplier interpolated to at death.
 * @property shape Spatial spawn distribution around the emitter's 2D origin.
 * @property speed Launch speed in world units per second. Not negative.
 * @property maxSpeed Optional upper bound on random launch speed (`>= speed`). Not negative.
 * @property directionDegrees Base launch angle in degrees (`0` = +X right, `90` = +Y up).
 * @property spreadDegrees Fan angle in degrees around [directionDegrees] (`0` = beam, `360` = circle).
 * @property radialSpeed Outward 2D speed added away from the emitter origin.
 * @property convergeToOrigin Sends spawned particles inward toward the origin at launch speed.
 * @property gravity Constant 2D acceleration in world units per second squared (`{x: 0, y: -9.8}`).
 * @property drag Linear velocity damping per second. Not negative.
 * @property turbulence Smooth sine flow-field perturbation strength (`0` disables).
 * @property turbulenceFrequency Spatial frequency of the turbulence field. Not negative.
 * @property spin Optional per-particle angular velocity range in degrees per second.
 * @property additive Draws with additive blending on `spriteScenePipeline(buildAdditive = true)`.
 * @property localSpace Keeps live particles attached to the node's `Transform` instead of trailing in world space.
 * @property inheritOrientation Rotates [shape] and launch direction by the node's Z rotation when [localSpace] is `false`.
 * @property sortingLayer Project sorting layer name, ordered alongside `sprite` and `tilemap`.
 * @property sortOrder Paint order within [sortingLayer]; higher values draw over lower ones.
 * @property burstCount Optional lifetime spawn cap; destroys the node once all spawned particles expire. Above 0.
 * @property burstCycle Optional repeating pulsed schedule replacing continuous [spawnRate].
 */
@Serializable
@SerialName("particle_emitter_2d")
data class SceneParticleEmitter2D(
    val texture: String = "",
    @PropertyRange(min = 1.0) val columns: Int = 1,
    @PropertyRange(min = 1.0) val rows: Int = 1,
    @PropertyRange(min = 1.0) val frameCount: Int = 1,
    @PropertyRange(min = 0.0) val frameRate: Float = DEFAULT_FRAME_RATE_2D,
    val enabled: Boolean = true,
    @PropertyRange(min = 0.0, exclusiveMin = true) val maxParticles: Int = DEFAULT_MAX_PARTICLES_2D,
    @PropertyRange(min = 0.0) val spawnRate: Float = DEFAULT_SPAWN_RATE_2D,
    @PropertyRange(min = 0.0, exclusiveMin = true) val lifetime: Float = DEFAULT_LIFETIME_2D,
    @PropertyRange(min = 0.0, exclusiveMin = true) val maxLifetime: Float? = null,
    @PropertyRange(min = 0.0, exclusiveMin = true) val size: Float = DEFAULT_SIZE_2D,
    @PropertyRange(min = 0.0) val endSize: Float? = null,
    @PropertyRange(min = 0.0, max = 1.0) val startAlpha: Float = 1f,
    val alphaCurve: SceneParticleAlphaCurve2D? = null,
    val tint: SceneColor = SceneColor(),
    val endTint: SceneColor? = null,
    val shape: SceneParticleShape2D = SceneParticleShape2D(),
    @PropertyRange(min = 0.0) val speed: Float = 1f,
    @PropertyRange(min = 0.0) val maxSpeed: Float? = null,
    val directionDegrees: Float = DEFAULT_DIRECTION_DEGREES_2D,
    @PropertyRange(min = 0.0, max = 360.0) val spreadDegrees: Float = FULL_CIRCLE_DEGREES_2D,
    val radialSpeed: Float = 0f,
    val convergeToOrigin: Boolean = false,
    val gravity: SceneVec2 = SceneVec2(0f, 0f),
    @PropertyRange(min = 0.0) val drag: Float = 0f,
    val turbulence: Float = 0f,
    @PropertyRange(min = 0.0) val turbulenceFrequency: Float = 1f,
    val spin: SceneParticleSpin2D? = null,
    val additive: Boolean = false,
    val localSpace: Boolean = false,
    val inheritOrientation: Boolean = false,
    val sortingLayer: String = SortingLayers.DEFAULT,
    val sortOrder: Int = 0,
    @PropertyRange(min = 0.0, exclusiveMin = true) val burstCount: Int? = null,
    val burstCycle: SceneParticleBurstCycle2D? = null,
) : SceneComponent {
    /** Total cells in the sheet (`0` when [columns] or [rows] is invalid). */
    val cellCount: Int
        get() = if (columns >= 1 && rows >= 1 && columns.toLong() * rows <= Int.MAX_VALUE) columns * rows else 0

    override fun validate(path: String): List<SceneValidationIssue> =
        (sheetProblems() + poolProblems() + lifetimeProblems() + sizeProblems() + velocityProblems() + forceProblems() + nestedProblems())
            .map { SceneValidationIssue(path, "particle_emitter_2d.$it") }

    private fun sheetProblems(): List<String> = buildList {
        if (texture.isNotEmpty() && texture.isBlank()) add("texture must not be blank")
        if (columns < 1) add("columns must be at least 1")
        if (rows < 1) add("rows must be at least 1")
        if (columns.toLong() * rows > Int.MAX_VALUE) add("sheet cell count must fit in an Int")
        if (frameCount < 1) {
            add("frameCount must be at least 1")
        } else if (cellCount > 0 && frameCount > cellCount) {
            add("frameCount must stay within the sheet's $cellCount cells: it is $frameCount")
        }
        val usesMultiCellSheet = columns > 1 || rows > 1 || frameCount > 1
        if (texture.isEmpty() && usesMultiCellSheet) {
            add("columns, rows and frameCount above 1 require a texture")
        }
        if (!frameRate.isNonNegativeFinite()) add("frameRate must be finite and not negative")
    }

    private fun poolProblems(): List<String> = buildList {
        if (maxParticles <= 0) add("maxParticles must be greater than 0")
        if (!spawnRate.isNonNegativeFinite()) add("spawnRate must be finite and not negative")
        if (sortingLayer.isBlank()) add("sortingLayer must name a layer")
        if (burstCount != null && burstCount <= 0) add("burstCount must be greater than 0")
    }

    private fun lifetimeProblems(): List<String> = buildList {
        if (!lifetime.isPositiveFinite()) add("lifetime must be finite and greater than 0")
        val upperLifetime = maxLifetime ?: return@buildList
        if (!upperLifetime.isPositiveFinite()) {
            add("maxLifetime must be finite and greater than 0")
        } else if (lifetime.isFinite() && upperLifetime < lifetime) {
            add("maxLifetime must not be below lifetime")
        }
    }

    private fun sizeProblems(): List<String> = buildList {
        val birthSize = this@SceneParticleEmitter2D.size
        if (!birthSize.isPositiveFinite()) add("size must be finite and greater than 0")
        if (endSize != null && !endSize.isNonNegativeFinite()) {
            add("endSize must be finite and not negative")
        }
        if (startAlpha !in 0f..1f) add("startAlpha must be within 0..1")
    }

    private fun velocityProblems(): List<String> = buildList {
        if (!speed.isNonNegativeFinite()) add("speed must be finite and not negative")
        if (maxSpeed != null) {
            if (!maxSpeed.isNonNegativeFinite()) {
                add("maxSpeed must be finite and not negative")
            } else if (speed.isFinite() && maxSpeed < speed) {
                add("maxSpeed must not be below speed")
            }
        }
        if (!directionDegrees.isFinite()) add("directionDegrees must be finite")
        if (spreadDegrees !in 0f..FULL_CIRCLE_DEGREES_2D) add("spreadDegrees must be within 0..360")
        if (!radialSpeed.isFinite()) add("radialSpeed must be finite")
    }

    private fun forceProblems(): List<String> = buildList {
        if (!gravity.x.isFinite() || !gravity.y.isFinite()) add("gravity must be finite")
        if (!drag.isNonNegativeFinite()) add("drag must be finite and not negative")
        if (!turbulence.isFinite()) add("turbulence must be finite")
        if (!turbulenceFrequency.isNonNegativeFinite()) {
            add("turbulenceFrequency must be finite and not negative")
        }
    }

    private fun nestedProblems(): List<String> =
        shape.problems() +
            alphaCurve?.problems().orEmpty() +
            spin?.problems().orEmpty() +
            burstCycle?.problems().orEmpty()
}

private fun Float.isNonNegativeFinite(): Boolean = this >= 0f && isFinite()

private fun Float.isPositiveFinite(): Boolean = this > 0f && isFinite()
