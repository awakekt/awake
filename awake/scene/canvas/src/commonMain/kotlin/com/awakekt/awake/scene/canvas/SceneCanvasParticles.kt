/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.binding.SavedOnly
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.binding.SceneBindingHooks
import com.awakekt.awake.scene.binding.SceneColorConverter
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec2
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasParticleAlphaCurve
import com.awakekt.awake.ui.canvas.CanvasParticleBurstCycle
import com.awakekt.awake.ui.canvas.CanvasParticlePlacement
import com.awakekt.awake.ui.canvas.CanvasParticleShape
import com.awakekt.awake.ui.canvas.CanvasParticleSpin
import com.awakekt.awake.ui.canvas.CanvasParticles
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DEFAULT_CANVAS_MAX_PARTICLES: Int = 64
private const val DEFAULT_CANVAS_SPAWN_RATE_SCENE: Float = 12f
private const val DEFAULT_CANVAS_LIFETIME_SCENE: Float = 0.8f
private const val DEFAULT_CANVAS_SIZE_DP_SCENE: Float = 8f
private const val DEFAULT_CANVAS_SPEED_DP_SCENE: Float = 48f
private const val DEFAULT_CANVAS_FRAME_RATE_SCENE: Float = 8f
private const val DEFAULT_CANVAS_DIRECTION_DEGREES_SCENE: Float = -90f
private const val FULL_CANVAS_SPREAD_DEGREES_SCENE: Float = 360f

/**
 * Authored canvas-space particle emitter (`canvas_particles`) attached to a canvas element node
 * alongside `canvas_rect`, backed by `awake:core:particles`'s shared `ParticleSimulation` in
 * logical canvas `dp` units.
 *
 * Leaving [texture] empty (`""`) draws solid-tinted quads; naming a project image loads and
 * premultiplies it through `loadCanvasImages` with optional [columns] × [rows] sprite-sheet cells.
 *
 * @property anchor Anchor on the owning element's `width × height` box where particles spawn.
 * @property offsetX Signed horizontal offset from [anchor] in `dp`.
 * @property offsetY Signed vertical offset from [anchor] in `dp`.
 * @property placement Paint layer relative to the element's background, widget, and text (`BehindContent` or `OverContent`).
 * @property clipped Honours enclosing clips and element masks when `true`; escapes scissor/path clips and canvas masks within the active render target when `false` (never escaping an ancestor offscreen `graphicsLayer` target).
 * @property localSpace Moves live particles with the element when `true`; trails in root canvas `dp` space when `false`.
 * @property additive Uses `BlendMode.Plus` when `true`, or premultiplied `BlendMode.SourceOver` when `false`.
 * @property texture Project image path for particle quads, or `""` for untextured solid-tinted quads.
 * @property columns Sprite-sheet columns when [texture] is non-empty. At least 1.
 * @property rows Sprite-sheet rows when [texture] is non-empty. At least 1.
 * @property frameCount Animated cells used in reading order from `0` (`1..columns*rows`). At least 1.
 * @property frameRate Playback rate in frames per second across [frameCount]. Not negative.
 * @property enabled Whether `CanvasOverlay` steps and draws this emitter.
 * @property maxParticles Fixed pool capacity; spawning pauses while every slot is live. Above 0.
 * @property spawnRate Continuous emission rate in particles per second. Not negative.
 * @property lifetime Minimum particle lifetime in seconds. Above 0.
 * @property maxLifetime Optional upper bound on random particle lifetime (`>= lifetime`). Above 0.
 * @property size Particle quad height in `dp` at birth (width preserves cell aspect). Above 0.
 * @property endSize Optional particle quad height in `dp` interpolated to at death. Not negative.
 * @property startAlpha Opacity at birth, from 0 to 1.
 * @property alphaCurve Optional fade-in / hold / fade-out envelope over normalized lifetime.
 * @property tint Straight-alpha colour multiplier at birth.
 * @property endTint Optional straight-alpha colour multiplier interpolated to at death.
 * @property shape Spatial spawn distribution in `dp` around the resolved anchor (`ElementRect` and `ElementEdge` use the element's `width × height`).
 * @property speed Launch speed in `dp` per second. Not negative.
 * @property maxSpeed Optional upper bound on random launch speed (`>= speed`). Not negative.
 * @property directionDegrees Base launch angle in degrees (`0` = right, `-90` = up on screen, `90` = down on screen).
 * @property spreadDegrees Fan angle in degrees around [directionDegrees] (`0` = beam, `360` = circle).
 * @property radialSpeed Outward speed in `dp/s` added away from the spawn origin.
 * @property convergeToOrigin Sends spawned particles inward toward the spawn origin at launch speed.
 * @property gravity Constant acceleration in `dp/s²` (`+Y` pulls downward on screen).
 * @property drag Linear velocity damping per second. Not negative.
 * @property turbulence Smooth sine flow-field perturbation strength (`0` disables).
 * @property turbulenceFrequency Spatial frequency of the turbulence field. Not negative.
 * @property spin Optional per-particle angular velocity range in degrees per second.
 * @property burstCount Optional lifetime spawn cap; the emitter finishes once all spawned particles expire, and [enabled] stays as authored. Above 0.
 * @property burstCycle Optional repeating pulsed emission schedule replacing continuous [spawnRate].
 */
@Serializable
@SerialName("canvas_particles")
@SceneBinding(
    CanvasParticles::class,
    converters = [SceneColorConverter::class],
    hooks = CanvasParticlesGravity::class,
    runtimeOnly = [
        "random", "gravityX", "gravityY", "dynamicSpawnRate", "onParticleDeath", "isFinished", "liveParticleCount",
        "droppedParticleCount",
    ],
)
data class SceneCanvasParticles(
    val anchor: CanvasAnchor = CanvasAnchor.Center,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val placement: CanvasParticlePlacement = CanvasParticlePlacement.OverContent,
    val clipped: Boolean = true,
    val localSpace: Boolean = true,
    val additive: Boolean = false,
    val texture: String = "",
    @PropertyRange(min = 1.0) val columns: Int = 1,
    @PropertyRange(min = 1.0) val rows: Int = 1,
    @PropertyRange(min = 1.0) val frameCount: Int = 1,
    @PropertyRange(min = 0.0) val frameRate: Float = DEFAULT_CANVAS_FRAME_RATE_SCENE,
    val enabled: Boolean = true,
    @PropertyRange(min = 0.0, exclusiveMin = true) val maxParticles: Int = DEFAULT_CANVAS_MAX_PARTICLES,
    @PropertyRange(min = 0.0) val spawnRate: Float = DEFAULT_CANVAS_SPAWN_RATE_SCENE,
    @PropertyRange(min = 0.0, exclusiveMin = true) val lifetime: Float = DEFAULT_CANVAS_LIFETIME_SCENE,
    @PropertyRange(min = 0.0, exclusiveMin = true) val maxLifetime: Float? = null,
    @PropertyRange(min = 0.0, exclusiveMin = true) val size: Float = DEFAULT_CANVAS_SIZE_DP_SCENE,
    @PropertyRange(min = 0.0) val endSize: Float? = null,
    @PropertyRange(min = 0.0, max = 1.0) val startAlpha: Float = 1f,
    val alphaCurve: CanvasParticleAlphaCurve? = null,
    val tint: SceneColor = SceneColor(),
    val endTint: SceneColor? = null,
    val shape: CanvasParticleShape = CanvasParticleShape(),
    @PropertyRange(min = 0.0) val speed: Float = DEFAULT_CANVAS_SPEED_DP_SCENE,
    @PropertyRange(min = 0.0) val maxSpeed: Float? = null,
    val directionDegrees: Float = DEFAULT_CANVAS_DIRECTION_DEGREES_SCENE,
    @PropertyRange(min = 0.0, max = 360.0) val spreadDegrees: Float = FULL_CANVAS_SPREAD_DEGREES_SCENE,
    val radialSpeed: Float = 0f,
    val convergeToOrigin: Boolean = false,
    @SavedOnly val gravity: SceneVec2 = SceneVec2(0f, 0f),
    @PropertyRange(min = 0.0) val drag: Float = 0f,
    val turbulence: Float = 0f,
    @PropertyRange(min = 0.0) val turbulenceFrequency: Float = 1f,
    val spin: CanvasParticleSpin? = null,
    @PropertyRange(min = 0.0, exclusiveMin = true) val burstCount: Int? = null,
    val burstCycle: CanvasParticleBurstCycle? = null,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    /** Total cells in the sheet (`0` when [columns] or [rows] is invalid). */
    val cellCount: Int
        get() = if (columns >= 1 && rows >= 1 && columns.toLong() * rows <= Int.MAX_VALUE) columns * rows else 0

    override fun validate(path: String): List<SceneValidationIssue> =
        (offsetProblems() + sheetProblems() + poolProblems() + lifetimeProblems() + sizeProblems() + velocityProblems() + forceProblems() + nestedProblems())
            .map { SceneValidationIssue(path, "canvas_particles.$it") }

    private fun offsetProblems(): List<String> = buildList {
        if (!offsetX.isFinite() || !offsetY.isFinite()) add("offset must be finite")
    }

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
        if (!frameRate.isNonNegativeFiniteScene()) add("frameRate must be finite and not negative")
    }

    private fun poolProblems(): List<String> = buildList {
        if (maxParticles <= 0) add("maxParticles must be greater than 0")
        if (!spawnRate.isNonNegativeFiniteScene()) add("spawnRate must be finite and not negative")
        if (burstCount != null && burstCount <= 0) add("burstCount must be greater than 0")
    }

    private fun lifetimeProblems(): List<String> = buildList {
        if (!lifetime.isPositiveFiniteScene()) add("lifetime must be finite and greater than 0")
        val upperLifetime = maxLifetime ?: return@buildList
        if (!upperLifetime.isPositiveFiniteScene()) {
            add("maxLifetime must be finite and greater than 0")
        } else if (lifetime.isFinite() && upperLifetime < lifetime) {
            add("maxLifetime must not be below lifetime")
        }
    }

    private fun sizeProblems(): List<String> = buildList {
        val birthSize = this@SceneCanvasParticles.size
        if (!birthSize.isPositiveFiniteScene()) add("size must be finite and greater than 0")
        if (endSize != null && !endSize.isNonNegativeFiniteScene()) {
            add("endSize must be finite and not negative")
        }
        if (startAlpha !in 0f..1f) add("startAlpha must be within 0..1")
    }

    private fun velocityProblems(): List<String> = buildList {
        if (!speed.isNonNegativeFiniteScene()) add("speed must be finite and not negative")
        if (maxSpeed != null) {
            if (!maxSpeed.isNonNegativeFiniteScene()) {
                add("maxSpeed must be finite and not negative")
            } else if (speed.isFinite() && maxSpeed < speed) {
                add("maxSpeed must not be below speed")
            }
        }
        if (!directionDegrees.isFinite()) add("directionDegrees must be finite")
        if (spreadDegrees !in 0f..FULL_CANVAS_SPREAD_DEGREES_SCENE) add("spreadDegrees must be within 0..360")
        if (!radialSpeed.isFinite()) add("radialSpeed must be finite")
    }

    private fun forceProblems(): List<String> = buildList {
        if (!gravity.x.isFinite() || !gravity.y.isFinite()) add("gravity must be finite")
        if (!drag.isNonNegativeFiniteScene()) add("drag must be finite and not negative")
        if (!turbulence.isFinite()) add("turbulence must be finite")
        if (!turbulenceFrequency.isNonNegativeFiniteScene()) {
            add("turbulenceFrequency must be finite and not negative")
        }
    }

    private fun nestedProblems(): List<String> =
        shape.problems() +
            alphaCurve?.problems().orEmpty() +
            spin?.problems().orEmpty() +
            burstCycle?.problems().orEmpty()
}

/**
 * Completes the generated `CanvasParticlesBinding`: the saved [SceneCanvasParticles.gravity] vector is
 * the live emitter's separate `gravityX` and `gravityY`.
 */
internal object CanvasParticlesGravity : SceneBindingHooks<CanvasParticles, SceneCanvasParticles> {
    override fun afterCopyToLive(saved: SceneCanvasParticles, live: CanvasParticles): CanvasParticles {
        live.gravityX = saved.gravity.x
        live.gravityY = saved.gravity.y
        return live
    }

    override fun afterCopyToSaved(live: CanvasParticles, saved: SceneCanvasParticles): SceneCanvasParticles =
        saved.copy(gravity = SceneVec2(live.gravityX, live.gravityY))
}

private fun Float.isNonNegativeFiniteScene(): Boolean = this >= 0f && isFinite()

private fun Float.isPositiveFiniteScene(): Boolean = this > 0f && isFinite()
