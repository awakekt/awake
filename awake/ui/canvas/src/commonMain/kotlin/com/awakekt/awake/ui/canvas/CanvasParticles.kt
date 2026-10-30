/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.TextureRegion
import com.awakekt.awake.core.particles.ParticleAlphaRamp
import com.awakekt.awake.core.particles.ParticleBurstSchedule
import com.awakekt.awake.core.particles.ParticlePlane
import com.awakekt.awake.core.particles.ParticleSimulation
import com.awakekt.awake.core.particles.ParticleSpinRange
import com.awakekt.awake.core.particles.UNLIMITED_BURST_COUNT
import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val DEFAULT_CANVAS_PARTICLE_CAPACITY: Int = 64
private const val DEFAULT_CANVAS_SPAWN_RATE: Float = 12f
private const val DEFAULT_CANVAS_LIFETIME: Float = 0.8f
private const val DEFAULT_CANVAS_SIZE_DP: Float = 8f
private const val DEFAULT_CANVAS_SPEED_DP: Float = 48f
private const val DEFAULT_CANVAS_SHEET_FPS: Float = 8f
private const val DEFAULT_CANVAS_DIRECTION_DEGREES: Float = -90f
private const val FULL_CANVAS_SPREAD_DEGREES: Float = 360f
private const val HALF_CANVAS_SPREAD: Float = 0.5f
private const val CANVAS_DEG_TO_RAD: Float = (PI / 180.0).toFloat()

/**
 * Where a [CanvasParticles] emitter paints relative to its owning [CanvasElement]'s own surface and
 * widget content.
 */
@Serializable
enum class CanvasParticlePlacement {
    /**
     * Paints behind the element's background, border, widget, text, and child elements, while
     * inheriting the element's rotation, scale, and opacity.
     */
    BehindContent,

    /**
     * Paints over the element's background, border, widget, and text, and behind child elements.
     */
    OverContent,
}

/**
 * Spatial spawn distribution kind for [CanvasParticleShape] in logical canvas `dp` units.
 */
@Serializable
enum class CanvasParticleShapeKind {
    /** Spawns directly at the emitter's resolved anchor point. */
    Point,

    /** Spawns uniformly by area inside a filled circle of [CanvasParticleShape.radius] `dp`. */
    Circle,

    /**
     * Spawns uniformly inside an annulus between [CanvasParticleShape.innerRadius] and
     * [CanvasParticleShape.radius] `dp` (circle perimeter when equal).
     */
    Ring,

    /**
     * Spawns uniformly inside `[-halfWidth..halfWidth, -halfHeight..halfHeight]` `dp` around the
     * anchor point.
     */
    Box,

    /**
     * Spawns uniformly inside the owning element's `width × height` `dp` rectangle shifted by
     * `offsetX` / `offsetY`.
     */
    ElementRect,

    /**
     * Spawns uniformly by arc length along the perimeter of the owning element's `width × height`
     * `dp` rectangle shifted by `offsetX` / `offsetY`.
     */
    ElementEdge,
}

/**
 * Spatial spawn region for [CanvasParticles] in logical canvas `dp` units.
 *
 * @property kind Distribution shape (`Point`, `Circle`, `Ring`, `Box`, `ElementRect`, or `ElementEdge`).
 * @property radius Outer circle radius for `Circle` and `Ring`, in `dp`. Not negative.
 * @property innerRadius Inner hole radius for `Ring`, in `dp`; at most [radius]. Not negative.
 * @property halfWidth Half-width along X for `Box`, in `dp`. Not negative.
 * @property halfHeight Half-height along Y for `Box`, in `dp`. Not negative.
 */
@Serializable
data class CanvasParticleShape(
    val kind: CanvasParticleShapeKind = CanvasParticleShapeKind.Point,
    @PropertyRange(min = 0.0) val radius: Float = 0f,
    @PropertyRange(min = 0.0) val innerRadius: Float = 0f,
    @PropertyRange(min = 0.0) val halfWidth: Float = 0f,
    @PropertyRange(min = 0.0) val halfHeight: Float = 0f,
) {
    /** Validation errors for this shape, or empty when valid. */
    fun problems(): List<String> = buildList {
        if (!radius.isNonNegativeFiniteCanvas()) add("shape.radius must be finite and not negative")
        if (!innerRadius.isNonNegativeFiniteCanvas()) add("shape.innerRadius must be finite and not negative")
        if (!halfWidth.isNonNegativeFiniteCanvas()) add("shape.halfWidth must be finite and not negative")
        if (!halfHeight.isNonNegativeFiniteCanvas()) add("shape.halfHeight must be finite and not negative")
        val validRadii = radius.isFinite() && innerRadius.isFinite()
        if (kind == CanvasParticleShapeKind.Ring && validRadii && innerRadius > radius) {
            add("shape.innerRadius must not exceed radius")
        }
    }

    /** Shared constants for [CanvasParticleShape]. */
    companion object {
        /** Shared default point shape (`0` bytes allocated when resetting pooled emitters). */
        val Default: CanvasParticleShape = CanvasParticleShape()
    }
}

/**
 * Opacity ramp over a canvas particle's normalized lifetime (`0..1`): fades in linearly from `0`
 * until [fadeInEnd], holds `startAlpha` until [fadeOutStart], then fades out linearly to `0` at `1`.
 *
 * @property fadeInEnd Normalized age (`0..1`) where opacity reaches `startAlpha`.
 * @property fadeOutStart Normalized age (`0..1`) where opacity begins fading toward `0`.
 */
@Serializable
data class CanvasParticleAlphaCurve(
    val fadeInEnd: Float = 0f,
    val fadeOutStart: Float = 0f,
) {
    /** Validation errors for this curve, or empty when valid. */
    fun problems(): List<String> = buildList {
        if (fadeInEnd !in 0f..1f || fadeOutStart !in 0f..1f) add("alphaCurve fractions must be within 0..1")
        if (fadeInEnd > fadeOutStart) add("alphaCurve.fadeInEnd must not come after fadeOutStart")
    }

    internal fun toRamp(): ParticleAlphaRamp = ParticleAlphaRamp(fadeInEnd, fadeOutStart)
}

/**
 * Per-particle angular velocity range for [CanvasParticles] in degrees per second.
 *
 * @property minDegreesPerSecond Minimum spin rate in degrees per second.
 * @property maxDegreesPerSecond Maximum spin rate in degrees per second.
 * @property randomStartAngle Whether each particle also starts at a uniform random angle.
 */
@Serializable
data class CanvasParticleSpin(
    val minDegreesPerSecond: Float,
    val maxDegreesPerSecond: Float,
    val randomStartAngle: Boolean = false,
) {
    /** Validation errors for this spin range, or empty when valid. */
    fun problems(): List<String> = buildList {
        if (!(minDegreesPerSecond.isFinite() && maxDegreesPerSecond.isFinite())) {
            add("spin rates must be finite")
        } else if (minDegreesPerSecond > maxDegreesPerSecond) {
            add("spin.minDegreesPerSecond must not be above maxDegreesPerSecond")
        }
    }

    internal fun toSpinRange(): ParticleSpinRange =
        ParticleSpinRange(minDegreesPerSecond, maxDegreesPerSecond, randomStartAngle)
}

/**
 * Looping pulsed emission schedule for [CanvasParticles], replacing continuous `spawnRate`.
 *
 * @property cycleSeconds Duration of one full cycle in seconds. Above 0.
 * @property activeSeconds Active window from the start of each cycle in seconds (`(0, cycleSeconds]`).
 * @property burstInterval Seconds between bursts inside the active window. Above 0.
 * @property burstSize Number of particles requested per burst. Above 0.
 */
@Serializable
data class CanvasParticleBurstCycle(
    val cycleSeconds: Float,
    val activeSeconds: Float,
    val burstInterval: Float,
    val burstSize: Int,
) {
    /** Validation errors for this burst schedule, or empty when valid. */
    fun problems(): List<String> = buildList {
        if (!(cycleSeconds > 0f && cycleSeconds.isFinite())) add("burstCycle.cycleSeconds must be greater than 0")
        if (!(activeSeconds > 0f && activeSeconds <= cycleSeconds)) {
            add("burstCycle.activeSeconds must be within 0 and cycleSeconds")
        }
        if (!(burstInterval > 0f && burstInterval.isFinite())) add("burstCycle.burstInterval must be greater than 0")
        if (burstSize <= 0) add("burstCycle.burstSize must be greater than 0")
    }

    internal fun toSchedule(): ParticleBurstSchedule =
        ParticleBurstSchedule(cycleSeconds, activeSeconds, burstInterval, burstSize)
}

/**
 * Focused particle emitter component attached to a [CanvasElement] (`element.particles`), backed by
 * `:awake:core:particles`'s zero-allocation [ParticleSimulation] in logical canvas `dp` units.
 *
 * **Coordinate space and UI scaling:**
 * Simulation positions, sizes, speeds, and accelerations are authored in logical canvas `dp`
 * (`+X` right, `+Y` down; `-90°` points upward on screen, `90°` downward). When [localSpace] is
 * `true` (default), particles move with the element (including subpixel motion and subtree scales).
 * When [localSpace] is `false`, spawned particles detach into root canvas `dp` space so moving the
 * element leaves a trail behind while mid-flight `CanvasScaler` or `scale` changes still scale
 * in-flight particles uniformly with the canvas.
 *
 * **Stepping and visibility:**
 * [CanvasOverlay] steps visible emitters once per frame using `LocalFrameClock.current.deltaSeconds`
 * (even when the element's rect sits off-screen or outside a clip), and **pauses stepping while the
 * element or an ancestor is hidden** (`visible == false` or hidden touch-only controls).
 *
 * **Clip escape (`clipped = false`):**
 * Setting [clipped] to `false` wraps drawing in `DrawScope.unclipped {}`, escaping enclosing
 * rectangle clips, path clips, and canvas masks (`CanvasMask`, circular `CanvasSlot`) within the
 * active render target. It **never** escapes an ancestor offscreen render-target layer
 * (`Modifier.graphicsLayer`, `CanvasStyle(rotation != 0f)`, or blurred layers). Note that
 * `CanvasStyle(alpha = ...)` / `Modifier.alpha` stays in the current render target without opening
 * an offscreen layer, so `clipped = false` inside a faded ancestor both escapes inner clips and
 * inherits the ancestor's premultiplied fade.
 *
 * @property maxParticles Fixed slot capacity of [simulation]; must be greater than `0`.
 * @param random Pseudo-random source for spawn positions, launch spread, frame offsets, and spin.
 */
class CanvasParticles(
    val maxParticles: Int = DEFAULT_CANVAS_PARTICLE_CAPACITY,
    random: Random = Random.Default,
) {
    /** Backing structure-of-arrays simulation pool in [ParticlePlane.XY]. */
    val simulation: ParticleSimulation = ParticleSimulation(maxParticles, random).also {
        it.plane = ParticlePlane.XY
    }

    /** Anchor on the owning element's `width × height` box where particles spawn (`Center` by default). */
    var anchor: CanvasAnchor = CanvasAnchor.Center

    /** Signed horizontal offset from [anchor] in `dp` (`+` right, `-` left). */
    var offsetX: Float = 0f

    /** Signed vertical offset from [anchor] in `dp` (`+` down, `-` up). */
    var offsetY: Float = 0f

    /** Paint layer relative to the owning element's background, widget, and text (`OverContent` by default). */
    var placement: CanvasParticlePlacement = CanvasParticlePlacement.OverContent

    /**
     * When `true` (default), honours enclosing clips and element masks; when `false`, draws inside
     * `DrawScope.unclipped {}` to escape scissor/path clips and canvas masks within the current
     * render target (never escaping an ancestor offscreen `graphicsLayer` target).
     */
    var clipped: Boolean = true

    /**
     * When `true` (default), particles stay attached to the element as it moves; when `false`,
     * spawned particles trail in root canvas `dp` space.
     */
    var localSpace: Boolean = true

    /** When `true`, draws particles with `BlendMode.Plus` instead of premultiplied `BlendMode.SourceOver`. */
    var additive: Boolean = false

    /** Key in `CanvasOverlay`'s `images` map, or `""` for untextured solid-tinted quads. */
    var texture: String = ""

    /** Sprite-sheet columns when [texture] is non-empty (`>= 1`). */
    var columns: Int = 1

    /** Sprite-sheet rows when [texture] is non-empty (`>= 1`). */
    var rows: Int = 1

    /** Animated cells cycled in reading order from `0` (`1..columns*rows`). */
    var frameCount: Int = 1

    /** Sprite-sheet playback rate in frames per second (`>= 0`). */
    var frameRate: Float = DEFAULT_CANVAS_SHEET_FPS

    /** Whether [CanvasOverlay] steps and draws this emitter. */
    var enabled: Boolean = true

    /** Continuous emission rate in particles per second (`>= 0`). */
    var spawnRate: Float = DEFAULT_CANVAS_SPAWN_RATE

    /** Minimum particle lifetime in seconds (`> 0`). */
    var lifetime: Float = DEFAULT_CANVAS_LIFETIME

    /** Optional upper bound on random particle lifetime (`>= lifetime`). */
    var maxLifetime: Float? = null

    /** Particle quad height in `dp` at birth (`> 0`); width preserves cell aspect ratio. */
    var size: Float = DEFAULT_CANVAS_SIZE_DP

    /** Optional particle quad height in `dp` interpolated to at death (`>= 0`). */
    var endSize: Float? = null

    /** Opacity at birth (`0..1`). */
    var startAlpha: Float = 1f

    /** Optional fade-in / hold / fade-out envelope over normalized lifetime. */
    var alphaCurve: CanvasParticleAlphaCurve? = null

    /** Straight-alpha colour multiplier at birth. */
    var tint: Color = Color.White

    /** Optional straight-alpha colour multiplier interpolated to at death. */
    var endTint: Color? = null

    /** Spatial spawn distribution in `dp` around the resolved anchor. */
    var shape: CanvasParticleShape = CanvasParticleShape()

    /** Launch speed in `dp` per second (`>= 0`). */
    var speed: Float = DEFAULT_CANVAS_SPEED_DP

    /** Optional upper bound on random launch speed (`>= speed`). */
    var maxSpeed: Float? = null

    /** Base launch angle in degrees (`0` = right, `-90` = up on screen, `90` = down on screen). */
    var directionDegrees: Float = DEFAULT_CANVAS_DIRECTION_DEGREES

    /** Fan angle in degrees around [directionDegrees] (`0` = beam, `360` = full circle). */
    var spreadDegrees: Float = FULL_CANVAS_SPREAD_DEGREES

    /** Outward speed in `dp/s` added away from the spawn origin. */
    var radialSpeed: Float = 0f

    /** Sends spawned particles inward toward the spawn origin at launch speed. */
    var convergeToOrigin: Boolean = false

    /** Constant X acceleration in `dp/s²`. */
    var gravityX: Float = 0f

    /** Constant Y acceleration in `dp/s²` (positive pulls downward on screen). */
    var gravityY: Float = 0f

    /** Linear velocity damping per second (`0` disables drag). */
    var drag: Float = 0f

    /** Smooth sine flow-field perturbation strength (`0` disables turbulence). */
    var turbulence: Float = 0f

    /** Spatial frequency of the turbulence flow field (`>= 0`). */
    var turbulenceFrequency: Float = 1f

    /** Optional per-particle angular velocity range in degrees per second. */
    var spin: CanvasParticleSpin? = null

    /** Optional lifetime spawn cap; disables [enabled] once all spawned particles expire. */
    var burstCount: Int? = null

    /** Optional repeating pulsed emission schedule replacing continuous [spawnRate]. */
    var burstCycle: CanvasParticleBurstCycle? = null

    /** Optional per-frame spawn-rate override evaluated without allocating. */
    var dynamicSpawnRate: (() -> Float)? = null

    /** Optional callback invoked with `(xDp, yDp)` right before an aged-out particle slot is cleared. */
    var onParticleDeath: ((xDp: Float, yDp: Float) -> Unit)? = null

    internal var owner: CanvasElement? = null
    internal var pooledForBurst: Boolean = false
    internal var autoHideOwnerOnBurstComplete: Boolean = false
    internal var lastFrameStamp: Long = 0L
    internal var lastDensityRatio: Float = 1f
    internal var cachedSourceBitmap: Any? = null
    internal var cachedPremultipliedBitmap: Any? = null
    private var cachedSheetCols: Int = 1
    private var cachedSheetRows: Int = 1
    private var cachedSheetRegions: Array<TextureRegion>? = null

    private val deathRelay: (Float, Float, Float) -> Unit = { x, y, _ ->
        onParticleDeath?.invoke(x, y)
    }

    /** Number of currently live particles (`0..maxParticles`). */
    val liveParticleCount: Int
        get() = simulation.liveParticleCount

    /** Cumulative count of burst/scheduled particles skipped because all [maxParticles] slots were live. */
    val droppedParticleCount: Long
        get() = simulation.droppedParticleCount

    init {
        syncToSimulation(0f, 0f)
    }

    /**
     * Immediately emits up to [count] particles into free pool slots using the current properties
     * and element geometry, without counting against [burstCount].
     *
     * Any unspawned remainder when the pool is full increments [droppedParticleCount] with zero
     * heap allocations. Returns the number actually spawned (`0..count`).
     */
    fun emit(count: Int): Int {
        val currentOwner = owner
        val widthDp = currentOwner?.width ?: 0f
        val heightDp = currentOwner?.height ?: 0f
        syncToSimulation(widthDp, heightDp)
        updateSimulationOrigin(currentOwner, widthDp, heightDp, lastDensityRatio)
        return simulation.emit(count)
    }

    /**
     * Steps the underlying [simulation] by [deltaSeconds] outside composition (for headless or unit
     * tests). Inside [CanvasOverlay], visible emitters are stepped automatically once per frame.
     */
    fun step(
        deltaSeconds: Float,
        elementWidthDp: Float = owner?.width ?: 0f,
        elementHeightDp: Float = owner?.height ?: 0f,
    ) {
        if (!enabled) return
        val currentOwner = owner
        syncToSimulation(elementWidthDp, elementHeightDp)
        updateSimulationOrigin(currentOwner, elementWidthDp, elementHeightDp, lastDensityRatio)
        val rateOverride = dynamicSpawnRate?.invoke() ?: Float.NaN
        val callback = if (onParticleDeath != null) deathRelay else null
        simulation.step(deltaSeconds, rateOverride, callback)
        if (simulation.isComplete) {
            enabled = false
            if (autoHideOwnerOnBurstComplete) currentOwner?.visible = false
        }
    }

    /** Clears all live particles, accumulators, and counters in [simulation]. */
    fun reset() {
        simulation.reset()
        lastFrameStamp = 0L
    }

    internal fun regionForFrame(frameIndex: Int, cols: Int, rows: Int): TextureRegion {
        if (cols <= 1 && rows <= 1) return TextureRegion.Whole
        var regions = cachedSheetRegions
        if (regions == null || cachedSheetCols != cols || cachedSheetRows != rows) {
            cachedSheetCols = cols
            cachedSheetRows = rows
            val invCols = 1f / cols
            val invRows = 1f / rows
            regions = Array(cols * rows) { cell ->
                val col = cell % cols
                val row = cell / cols
                TextureRegion(col * invCols, row * invRows, (col + 1) * invCols, (row + 1) * invRows)
            }
            cachedSheetRegions = regions
        }
        return regions[frameIndex.coerceIn(0, regions.lastIndex)]
    }

    internal fun resetDefaults() {
        anchor = CanvasAnchor.Center
        offsetX = 0f
        offsetY = 0f
        placement = CanvasParticlePlacement.OverContent
        clipped = true
        localSpace = true
        additive = false
        texture = ""
        columns = 1
        rows = 1
        frameCount = 1
        frameRate = DEFAULT_CANVAS_SHEET_FPS
        enabled = true
        spawnRate = 0f
        lifetime = DEFAULT_CANVAS_LIFETIME
        maxLifetime = null
        size = DEFAULT_CANVAS_SIZE_DP
        endSize = null
        startAlpha = 1f
        alphaCurve = null
        tint = Color.White
        endTint = null
        shape = CanvasParticleShape.Default
        speed = DEFAULT_CANVAS_SPEED_DP
        maxSpeed = null
        directionDegrees = DEFAULT_CANVAS_DIRECTION_DEGREES
        spreadDegrees = FULL_CANVAS_SPREAD_DEGREES
        radialSpeed = 0f
        convergeToOrigin = false
        gravityX = 0f
        gravityY = 0f
        drag = 0f
        turbulence = 0f
        turbulenceFrequency = 1f
        spin = null
        burstCount = null
        burstCycle = null
        dynamicSpawnRate = null
        onParticleDeath = null
    }
}

internal fun CanvasParticles.localAnchorDpX(elementWidthDp: Float): Float = when (shape.kind) {
    CanvasParticleShapeKind.ElementRect,
    CanvasParticleShapeKind.ElementEdge,
    -> elementWidthDp * 0.5f + offsetX
    else -> anchor.column * 0.5f * elementWidthDp + offsetX
}

internal fun CanvasParticles.localAnchorDpY(elementHeightDp: Float): Float = when (shape.kind) {
    CanvasParticleShapeKind.ElementRect,
    CanvasParticleShapeKind.ElementEdge,
    -> elementHeightDp * 0.5f + offsetY
    else -> anchor.row * 0.5f * elementHeightDp + offsetY
}

internal fun CanvasParticles.updateSimulationOrigin(
    element: CanvasElement?,
    elementWidthDp: Float,
    elementHeightDp: Float,
    densityRatio: Float,
) {
    if (localSpace) {
        simulation.setOrigin(0f, 0f, 0f)
        return
    }
    val anchorX = localAnchorDpX(elementWidthDp) * densityRatio
    val anchorY = localAnchorDpY(elementHeightDp) * densityRatio
    val placed = element?.placedBounds
    if (placed != null) {
        simulation.setOrigin(placed.x + anchorX, placed.y + anchorY, 0f)
    } else {
        val baseX = element?.offsetX ?: 0f
        val baseY = element?.offsetY ?: 0f
        simulation.setOrigin(baseX + anchorX, baseY + anchorY, 0f)
    }
}

internal fun CanvasParticles.syncToSimulation(elementWidthDp: Float, elementHeightDp: Float) {
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

    syncVelocityAndForces(sim)
    syncShapeAndCurves(sim, elementWidthDp, elementHeightDp)
}

private fun CanvasParticles.syncVelocityAndForces(sim: ParticleSimulation) {
    val minSpd = speed
    val maxSpd = maxSpeed
    val hasSpeedRange = maxSpd != null && maxSpd > minSpd
    val dirRadians = directionDegrees * CANVAS_DEG_TO_RAD
    val dirScale = if (hasSpeedRange) 1f else minSpd
    sim.baseVelX = cos(dirRadians) * dirScale
    sim.baseVelY = sin(dirRadians) * dirScale
    sim.baseVelZ = 0f
    sim.minSpeed = minSpd
    sim.maxSpeed = if (maxSpd != null && maxSpd > minSpd) maxSpd else minSpd
    sim.velocityJitter = 0f
    val clampedSpread = spreadDegrees.coerceAtLeast(0f)
    sim.hasConeHalfAngle = clampedSpread > 0f || hasSpeedRange
    sim.coneHalfAngleDegrees = clampedSpread * HALF_CANVAS_SPREAD
    sim.radialSpeed = radialSpeed
    sim.convergeToOrigin = convergeToOrigin
    sim.accelX = gravityX
    sim.accelY = gravityY
    sim.accelZ = 0f
    sim.drag = drag
    sim.turbulence = turbulence
    sim.turbulenceFrequency = turbulenceFrequency
    sim.burstLimit = burstCount ?: if (pooledForBurst) 0 else UNLIMITED_BURST_COUNT
}

private fun CanvasParticles.syncShapeAndCurves(
    sim: ParticleSimulation,
    elementWidthDp: Float,
    elementHeightDp: Float,
) {
    val currentShape = shape
    when (currentShape.kind) {
        CanvasParticleShapeKind.Point -> sim.spawnShape.setPoint()
        CanvasParticleShapeKind.Circle -> sim.spawnShape.setRingXY(0f, currentShape.radius)
        CanvasParticleShapeKind.Ring -> sim.spawnShape.setRingXY(currentShape.innerRadius, currentShape.radius)
        CanvasParticleShapeKind.Box -> sim.spawnShape.setBox(currentShape.halfWidth, currentShape.halfHeight)
        CanvasParticleShapeKind.ElementRect ->
            sim.spawnShape.setBox(elementWidthDp * 0.5f, elementHeightDp * 0.5f)
        CanvasParticleShapeKind.ElementEdge ->
            sim.spawnShape.setRectEdge(elementWidthDp * 0.5f, elementHeightDp * 0.5f)
    }
    val curve = alphaCurve
    if (curve == null) sim.alphaRamp.clear() else sim.alphaRamp.set(curve.fadeInEnd, curve.fadeOutStart)
    val spinRange = spin
    if (spinRange == null) {
        sim.spin.clear()
    } else {
        sim.spin.set(spinRange.minDegreesPerSecond, spinRange.maxDegreesPerSecond, spinRange.randomStartAngle)
    }
    val cycle = burstCycle
    if (cycle == null) {
        sim.burstSchedule.clear()
    } else {
        sim.burstSchedule.set(cycle.cycleSeconds, cycle.activeSeconds, cycle.burstInterval, cycle.burstSize)
    }
}

internal fun Float.isNonNegativeFiniteCanvas(): Boolean = isFinite() && this >= 0f
