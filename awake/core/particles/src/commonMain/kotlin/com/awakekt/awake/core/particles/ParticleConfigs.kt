/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.particles

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val SHAPE_POINT: Int = 0
internal const val SHAPE_RING_XZ: Int = 1
internal const val SHAPE_RING_XY: Int = 2
internal const val SHAPE_BOX: Int = 3
internal const val SHAPE_RECT_EDGE: Int = 4

private const val ORIENTATION_FLOATS: Int = 9
private const val AXIS_Y_OFFSET: Int = 3
private const val AXIS_Z_OFFSET: Int = 6
private const val AXIS_STRIDE: Int = 3

/**
 * Reusable 3×3 orthonormal basis for rotating local spawn positions and velocities without
 * allocating matrices per frame.
 */
class ParticleOrientationBasis {
    internal val axes: FloatArray = FloatArray(ORIENTATION_FLOATS).apply {
        this[0] = 1f
        this[AXIS_Y_OFFSET + 1] = 1f
        this[AXIS_Z_OFFSET + 2] = 1f
    }

    /** Resets this basis to the world identity axes. */
    fun setIdentity() {
        axes.fill(0f)
        axes[0] = 1f
        axes[AXIS_Y_OFFSET + 1] = 1f
        axes[AXIS_Z_OFFSET + 2] = 1f
    }

    /**
     * Loads the rotation axes from [matrix] with any column scaling divided out, or resets to
     * identity when [matrix] is `null`.
     */
    fun setFromMatrix(matrix: Mat4?) {
        if (matrix == null) {
            setIdentity()
            return
        }
        writeAxis(0, matrix.m00, matrix.m10, matrix.m20)
        writeAxis(AXIS_Y_OFFSET, matrix.m01, matrix.m11, matrix.m21)
        writeAxis(AXIS_Z_OFFSET, matrix.m02, matrix.m12, matrix.m22)
    }

    /** Sets a 2D counter-clockwise rotation of [radians] around the +Z axis. */
    fun setRotationZ(radians: Float) {
        val c = cos(radians)
        val s = sin(radians)
        axes.fill(0f)
        axes[0] = c
        axes[1] = s
        axes[AXIS_Y_OFFSET] = -s
        axes[AXIS_Y_OFFSET + 1] = c
        axes[AXIS_Z_OFFSET + 2] = 1f
    }

    /** Copies all nine basis scalars from [other] in place without allocating. */
    fun copyFrom(other: ParticleOrientationBasis) {
        other.axes.copyInto(axes)
    }

    private fun writeAxis(offset: Int, x: Float, y: Float, z: Float) {
        val length = sqrt(x * x + y * y + z * z)
        val unit = if (length > 0f) 1f / length else 0f
        axes[offset] = x * unit
        axes[offset + 1] = y * unit
        axes[offset + 2] = z * unit
        if (length == 0f) axes[offset + offset / AXIS_STRIDE] = 1f
    }
}

/**
 * Zero-allocation mutable spawn-shape holder owned by a [ParticleSimulation].
 */
class ParticleSpawnConfig {
    internal var kind: Int = SHAPE_POINT
    internal var p0: Float = 0f
    internal var p1: Float = 0f
    internal var p2: Float = 0f

    /** Configures point emission directly at the origin. */
    fun setPoint() {
        kind = SHAPE_POINT
        p0 = 0f
        p1 = 0f
        p2 = 0f
    }

    /** Configures horizontal 3D ring emission of [radius] in the local `XZ` plane. */
    fun setRingXZ(radius: Float) {
        if (radius <= 0f) {
            setPoint()
        } else {
            kind = SHAPE_RING_XZ
            p0 = radius
            p1 = 0f
            p2 = 0f
        }
    }

    /** Configures 2D/UI ring or disc emission between [innerRadius] and [outerRadius] in the local `XY` plane. */
    fun setRingXY(innerRadius: Float = 0f, outerRadius: Float) {
        if (outerRadius <= 0f) {
            setPoint()
        } else {
            kind = SHAPE_RING_XY
            p0 = innerRadius.coerceAtLeast(0f).coerceAtMost(outerRadius)
            p1 = outerRadius
            p2 = 0f
        }
    }

    /** Configures uniform box/rectangle emission within `[-halfExtentX..halfExtentX, -halfExtentY..halfExtentY, -halfExtentZ..halfExtentZ]`. */
    fun setBox(halfExtentX: Float, halfExtentY: Float, halfExtentZ: Float = 0f) {
        kind = SHAPE_BOX
        p0 = halfExtentX.coerceAtLeast(0f)
        p1 = halfExtentY.coerceAtLeast(0f)
        p2 = halfExtentZ.coerceAtLeast(0f)
    }

    /** Configures perimeter emission along a 2D rectangle of [halfWidth] by [halfHeight] in the local `XY` plane. */
    fun setRectEdge(halfWidth: Float, halfHeight: Float) {
        kind = SHAPE_RECT_EDGE
        p0 = halfWidth.coerceAtLeast(0f)
        p1 = halfHeight.coerceAtLeast(0f)
        p2 = 0f
    }

    /** Copies [shape] into this holder without retaining any wrapper reference on future updates. */
    fun set(shape: ParticleSpawnShape) {
        when (shape) {
            ParticleSpawnShape.Point -> setPoint()
            is ParticleSpawnShape.RingXZ -> setRingXZ(shape.radius)
            is ParticleSpawnShape.RingXY -> setRingXY(shape.innerRadius, shape.outerRadius)
            is ParticleSpawnShape.Box -> setBox(shape.halfExtentX, shape.halfExtentY, shape.halfExtentZ)
            is ParticleSpawnShape.RectEdge -> setRectEdge(shape.halfWidth, shape.halfHeight)
        }
    }
}

/**
 * Zero-allocation mutable opacity-ramp holder owned by a [ParticleSimulation].
 */
class ParticleAlphaConfig {
    /** Whether a custom opacity ramp is active (`false` uses linear fade from `startAlpha` to `0`). */
    var enabled: Boolean = false
        private set

    /** Normalized fade-in end fraction (`0..1`) when [enabled] is `true`. */
    var fadeInEnd: Float = 0f
        private set

    /** Normalized fade-out start fraction (`0..1`) when [enabled] is `true`. */
    var fadeOutStart: Float = 0f
        private set

    /** Activates an opacity ramp with [fadeInEnd] and [fadeOutStart] in `0..1`. */
    fun set(fadeInEnd: Float, fadeOutStart: Float) {
        enabled = true
        this.fadeInEnd = fadeInEnd.coerceIn(0f, 1f)
        this.fadeOutStart = fadeOutStart.coerceIn(this.fadeInEnd, 1f)
    }

    /** Copies [ramp] into this holder, or clears it when [ramp] is `null`. */
    fun set(ramp: ParticleAlphaRamp?) {
        if (ramp == null) clear() else set(ramp.fadeInEnd, ramp.fadeOutStart)
    }

    /** Restores the default linear fade from birth opacity to zero. */
    fun clear() {
        enabled = false
        fadeInEnd = 0f
        fadeOutStart = 0f
    }
}

/**
 * Zero-allocation mutable spin-range holder owned by a [ParticleSimulation].
 */
class ParticleSpinConfig {
    /** Whether spawned particles draw an angular velocity (`false` leaves rotation at `0`). */
    var enabled: Boolean = false
        private set

    /** Minimum spin in degrees per second when [enabled] is `true`. */
    var minDegreesPerSecond: Float = 0f
        private set

    /** Maximum spin in degrees per second when [enabled] is `true`. */
    var maxDegreesPerSecond: Float = 0f
        private set

    /** Whether spawned particles also pick a random starting angle in `[0, 2π)`. */
    var randomStartAngle: Boolean = false
        private set

    /** Activates per-particle spin between [minDegreesPerSecond] and [maxDegreesPerSecond]. */
    fun set(minDegreesPerSecond: Float, maxDegreesPerSecond: Float, randomStartAngle: Boolean = false) {
        enabled = true
        this.minDegreesPerSecond = minDegreesPerSecond
        this.maxDegreesPerSecond = maxDegreesPerSecond
        this.randomStartAngle = randomStartAngle
    }

    /** Copies [spin] into this holder, or clears it when [spin] is `null`. */
    fun set(spin: ParticleSpinRange?) {
        if (spin == null) clear() else set(spin.minDegreesPerSecond, spin.maxDegreesPerSecond, spin.randomStartAngle)
    }

    /** Disables per-particle spin so future spawns stay upright. */
    fun clear() {
        enabled = false
        minDegreesPerSecond = 0f
        maxDegreesPerSecond = 0f
        randomStartAngle = false
    }
}

/**
 * Zero-allocation mutable pulsed-burst schedule holder owned by a [ParticleSimulation].
 */
class ParticleBurstConfig {
    /** Whether a pulsed burst schedule replaces continuous [ParticleSimulation.spawnRate]. */
    var enabled: Boolean = false
        private set

    /** Duration of one full cycle in seconds. */
    var cycleSeconds: Float = 1f
        private set

    /** Active window at the start of each cycle in seconds. */
    var activeSeconds: Float = 1f
        private set

    /** Interval between bursts inside the active window in seconds. */
    var burstInterval: Float = 1f
        private set

    /** Particles requested per scheduled burst. */
    var burstSize: Int = 1
        private set

    /**
     * Activates a pulsed burst schedule with scalar parameters, validated as [ParticleBurstSchedule]
     * validates them.
     *
     * @throws IllegalArgumentException when [cycleSeconds] or [burstInterval] is not positive and
     * finite, [activeSeconds] is outside `(0, cycleSeconds]`, or [burstSize] is not positive. The
     * holder is left unchanged.
     */
    fun set(cycleSeconds: Float, activeSeconds: Float, burstInterval: Float, burstSize: Int) {
        requireValidBurstSchedule(cycleSeconds, activeSeconds, burstInterval, burstSize)
        enabled = true
        this.cycleSeconds = cycleSeconds
        this.activeSeconds = activeSeconds
        this.burstInterval = burstInterval
        this.burstSize = burstSize
    }

    /** Copies [schedule] into this holder, or clears it when [schedule] is `null`. */
    fun set(schedule: ParticleBurstSchedule?) {
        if (schedule == null) {
            clear()
        } else {
            set(schedule.cycleSeconds, schedule.activeSeconds, schedule.burstInterval, schedule.burstSize)
        }
    }

    /** Disables pulsed scheduling so continuous [ParticleSimulation.spawnRate] applies. */
    fun clear() {
        enabled = false
    }
}

/**
 * Zero-allocation mutable ground-collision configuration owned by a [ParticleSimulation].
 */
class ParticleGroundConfig {
    /** Whether a flat landing plane at [groundY] is active. */
    var hasGroundY: Boolean = false
        private set

    /** Height of the flat landing plane when [hasGroundY] is `true`. */
    var groundY: Float = 0f
        private set

    /**
     * Optional ground height under a point, taking priority over [colliders] and [groundY]. It
     * returns `Float.NaN` where there is no ground.
     */
    var groundHeightProvider: ParticleGroundHeight? = null

    /** World-space boxes whose top faces (`max.y`) catch falling particles inside their `XZ` footprint. */
    var colliders: List<Aabb> = emptyList()

    /** Vertical bounce factor (`0` settles immediately; `1` reflects without loss). */
    var restitution: Float = 0f

    /** Horizontal (`X`/`Z`) velocity multiplier applied on each bounce (`1` keeps speed). */
    var friction: Float = 1f

    /** Minimum post-bounce vertical speed before a bouncing particle settles to rest. */
    var bounceStopVelocity: Float = DEFAULT_BOUNCE_STOP_VELOCITY

    internal val hasAnySurface: Boolean
        get() = hasGroundY || groundHeightProvider != null || colliders.isNotEmpty()

    /** Sets or clears (`null`) the flat landing height without boxing on subsequent steps. */
    fun setGroundY(y: Float?) {
        if (y == null) {
            hasGroundY = false
            groundY = 0f
        } else {
            hasGroundY = true
            groundY = y
        }
    }

    /** Clears all ground collision surfaces and resets bounce coefficients to defaults. */
    fun clear() {
        hasGroundY = false
        groundY = 0f
        groundHeightProvider = null
        colliders = emptyList()
        restitution = 0f
        friction = 1f
        bounceStopVelocity = DEFAULT_BOUNCE_STOP_VELOCITY
    }
}
