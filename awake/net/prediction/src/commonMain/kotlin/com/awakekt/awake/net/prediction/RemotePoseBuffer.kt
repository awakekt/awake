/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Bounded, zero-allocation ring buffer that interpolates one remote networked entity's pose at a
 * given server playback timestamp (typically from [AdaptiveSnapshotTimeline.playbackTime]).
 *
 * - Between two buffered snapshots it linearly interpolates position, pitch, and shortest-arc yaw,
 *   and adopts the ground-contact flag of the snapshot playback has reached.
 * - Past the newest snapshot (an underrun, counted in [underruns]) it extrapolates along the last
 *   observed velocity for at most [extrapolationSeconds], then holds. Grounded entities keep their
 *   height while extrapolating so landings do not sink into floors.
 * - When a fresh snapshot arrives after an underrun, the residual extrapolation error decays
 *   smoothly over [BLEND_SECONDS] of playback time instead of snapping.
 * - Discontinuities faster than [WARP_METRES_PER_SECOND] and farther than [WARP_METRES] snap
 *   directly when playback reaches them rather than interpolating across teleports.
 * - Entities sent at decimated interest-tier cadences (every 2nd or 4th snapshot) can call
 *   [noteSkipped] on omitted snapshots and subtract [cadenceDelay] from the shared playback time
 *   so distant entities never stutter or underrun without widening the buffer for nearby entities.
 *
 * All history slots are stored in primitive arrays; neither [push], [noteSkipped], [cadenceDelay],
 * nor [sample] allocates after construction.
 *
 * @property extrapolationSeconds Maximum lead extrapolated past the newest snapshot, in seconds.
 * @property capacity Number of buffered snapshot poses retained (at least `2`).
 * @property blendSeconds Exponential time constant decaying post-underrun extrapolation error.
 * @property warpMetres Minimum jump distance in metres before [warpSpeedMetresPerSecond] snaps.
 * @property warpSpeedMetresPerSecond Implied speed above which a transition snaps instead of interpolating.
 */
@Suppress("TooManyFunctions")
class RemotePoseBuffer(
    val extrapolationSeconds: Double = DEFAULT_EXTRAPOLATION_SECONDS,
    val capacity: Int = DEFAULT_CAPACITY,
    val blendSeconds: Double = BLEND_SECONDS,
    val warpMetres: Float = WARP_METRES,
    val warpSpeedMetresPerSecond: Double = WARP_METRES_PER_SECOND,
) {
    init {
        require(extrapolationSeconds.isFinite() && extrapolationSeconds >= 0.0) {
            "extrapolationSeconds must be finite and non-negative, was $extrapolationSeconds."
        }
        require(capacity >= 2) { "capacity must be at least 2, was $capacity." }
        require(blendSeconds.isFinite() && blendSeconds > 0.0) {
            "blendSeconds must be finite and positive, was $blendSeconds."
        }
        require(warpMetres.isFinite() && warpMetres >= 0f) {
            "warpMetres must be finite and non-negative, was $warpMetres."
        }
        require(warpSpeedMetresPerSecond.isFinite() && warpSpeedMetresPerSecond > 0.0) {
            "warpSpeedMetresPerSecond must be finite and positive, was $warpSpeedMetresPerSecond."
        }
    }

    private val times = DoubleArray(capacity)
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private val zs = FloatArray(capacity)
    private val yaws = FloatArray(capacity)
    private val pitches = FloatArray(capacity)
    private val contacts = BooleanArray(capacity)

    private var start = 0
    private var count = 0

    /** Sampled world/scene X coordinate after the latest [sample]. */
    var x: Float = 0f
        private set

    /** Sampled world/scene Y coordinate after the latest [sample]. */
    var y: Float = 0f
        private set

    /** Sampled world/scene Z coordinate after the latest [sample]. */
    var z: Float = 0f
        private set

    /** Sampled yaw in radians after the latest [sample]. */
    var yaw: Float = 0f
        private set

    /** Sampled pitch (e.g. degrees or radians in the caller's convention) after the latest [sample]. */
    var pitch: Float = 0f
        private set

    /** Sampled ground-contact flag after the latest [sample]. */
    var grounded: Boolean = true
        private set

    /** Derived instantaneous X velocity in metres/second after the latest [sample] (zero while held or warped). */
    var velocityX: Float = 0f
        private set

    /** Derived instantaneous Y velocity in metres/second after the latest [sample] (zero while held or warped). */
    var velocityY: Float = 0f
        private set

    /** Derived instantaneous Z velocity in metres/second after the latest [sample] (zero while held or warped). */
    var velocityZ: Float = 0f
        private set

    /** Derived 3D speed in metres/second after the latest [sample], suitable for driving animations. */
    val speed: Float
        get() = distance(velocityX, velocityY, velocityZ)

    /** Derived horizontal (XZ) speed in metres/second after the latest [sample]. */
    val planarSpeed: Float
        get() = sqrt(velocityX * velocityX + velocityZ * velocityZ)

    /** Whether the latest [sample] ran past [newestTime]. */
    var underrun: Boolean = false
        private set

    /** Cumulative count of samples that ran past [newestTime]. */
    var underruns: Long = 0L
        private set

    /** Whether at least one snapshot has been pushed since construction or [reset]. */
    val hasSamples: Boolean
        get() = count > 0

    /** Server timestamp of the newest buffered snapshot, or `Double.NaN` when empty. */
    val newestTime: Double
        get() = if (count == 0) Double.NaN else times[at(count - 1)]

    /** Client clock elapsed between the last two snapshots pushed with a finite `arrivedAt`. */
    var arrivalGap: Double = Double.NaN
        private set

    /** Distance in metres between the drawn pose and the raw buffered curve (extrapolation + blend). */
    var renderError: Float = 0f
        private set

    private var arrived = Double.NaN
    private var baseTime = 0.0
    private var baseX = 0f
    private var baseY = 0f
    private var baseZ = 0f
    private var extrapVx = 0f
    private var extrapVy = 0f
    private var extrapVz = 0f
    private var errorX = 0f
    private var errorY = 0f
    private var errorZ = 0f
    private var lastTime = Double.NaN
    private var pushed = false

    private var prevServerGap = Double.NaN
    private var serverGap = Double.NaN
    private var skippedGap = 0.0
    private var hadSkipBeforePush = false
    private var slack = 0.0

    /**
     * Appends an authoritative pose at [serverTimeSeconds]. Out-of-order timestamps older than
     * [newestTime] are ignored; an equal timestamp overwrites the newest slot in place.
     */
    @Suppress("LongParameterList")
    fun push(
        serverTimeSeconds: Double,
        x: Float,
        y: Float,
        z: Float,
        yaw: Float = 0f,
        pitch: Float = 0f,
        grounded: Boolean = true,
        arrivedAtSeconds: Double = Double.NaN,
    ) {
        if (!arrivedAtSeconds.isNaN()) {
            if (!arrived.isNaN()) arrivalGap = arrivedAtSeconds - arrived
            arrived = arrivedAtSeconds
        }
        if (count > 0) {
            val last = times[at(count - 1)]
            if (serverTimeSeconds < last) return
            if (serverTimeSeconds == last) {
                count--
            } else {
                prevServerGap = serverGap
                serverGap = serverTimeSeconds - last
                hadSkipBeforePush = skippedGap > 0.0
                skippedGap = 0.0
            }
        }
        if (count == capacity) {
            start = (start + 1) % capacity
            count--
        }
        val i = at(count)
        times[i] = serverTimeSeconds
        xs[i] = x
        ys[i] = y
        zs[i] = z
        yaws[i] = yaw
        pitches[i] = pitch
        contacts[i] = grounded
        count++
        pushed = true
    }

    /**
     * Notes that a server snapshot at [snapshotTimeSeconds] intentionally omitted this entity due
     * to interest-tier decimation, allowing [cadenceDelay] to hold extra delay smoothly.
     */
    fun noteSkipped(snapshotTimeSeconds: Double) {
        val newest = newestTime
        if (!newest.isNaN() && snapshotTimeSeconds > newest) {
            skippedGap = snapshotTimeSeconds - newest
        }
    }

    /**
     * Extra per-entity delay in seconds needed when snapshots arrive at multiples of
     * [baseIntervalSeconds]. Slews monotonically without rewinding effective playback time.
     */
    fun cadenceDelay(baseIntervalSeconds: Double, deltaSeconds: Float): Double {
        if (!baseIntervalSeconds.isFinite() || baseIntervalSeconds <= 0.0) return 0.0
        val minFar = baseIntervalSeconds * 1.4
        val maxFar = baseIntervalSeconds * (MAX_CADENCE_INTERVALS + 1.5)
        val pushSpan = if (
            serverGap in minFar..maxFar &&
            (hadSkipBeforePush || prevServerGap in minFar..maxFar)
        ) {
            serverGap - baseIntervalSeconds
        } else {
            0.0
        }
        val skipSpan = if (skippedGap > baseIntervalSeconds * 0.4) skippedGap else 0.0
        val target = maxOf(pushSpan, skipSpan).coerceIn(0.0, baseIntervalSeconds * MAX_CADENCE_INTERVALS)
        val dt = deltaSeconds.toDouble().coerceAtLeast(0.0)
        slack += if (target > slack) {
            min(target - slack, dt * CADENCE_GROW_RATE)
        } else {
            -min(slack - target, dt * CADENCE_SHRINK_RATE)
        }
        return slack
    }

    /**
     * Evaluates the buffered pose at [playbackTimeSeconds], writing [x], [y], [z], [yaw], [pitch],
     * [grounded], [underrun], and [renderError] in place. Returns `false` when the buffer is empty.
     */
    fun sample(playbackTimeSeconds: Double): Boolean {
        if (count == 0) return false
        val wasUnderrun = underrun
        val prevX = x
        val prevY = y
        val prevZ = z
        val prevTime = lastTime
        val hadPush = pushed
        pushed = false
        lastTime = playbackTimeSeconds

        val oldest = at(0)
        val newest = at(count - 1)
        when {
            playbackTimeSeconds <= times[oldest] -> {
                underrun = false
                setFrom(oldest)
                clearVelocity()
            }
            playbackTimeSeconds < times[newest] -> {
                underrun = false
                interpolate(playbackTimeSeconds)
            }
            else -> {
                underrun = true
                underruns++
                extrapolate(playbackTimeSeconds, newest, wasUnderrun, hadPush)
            }
        }

        val rawX = x
        val rawY = y
        val rawZ = z
        if (wasUnderrun && !underrun && extrapolationSeconds > 0.0) {
            startBlend(prevX - rawX, prevY - rawY, prevZ - rawZ)
        }
        if (!underrun) decayBlend(prevTime, playbackTimeSeconds)
        renderError = if (underrun) {
            distance(x - xs[newest], y - ys[newest], z - zs[newest])
        } else {
            distance(x - rawX, y - rawY, z - rawZ)
        }
        return true
    }

    /** Clears all buffered poses, telemetry counters, velocity, and blend state for component reuse. */
    fun reset() {
        start = 0
        count = 0
        x = 0f
        y = 0f
        z = 0f
        yaw = 0f
        pitch = 0f
        grounded = true
        clearVelocity()
        underrun = false
        underruns = 0L
        arrivalGap = Double.NaN
        renderError = 0f
        arrived = Double.NaN
        baseTime = 0.0
        baseX = 0f
        baseY = 0f
        baseZ = 0f
        extrapVx = 0f
        extrapVy = 0f
        extrapVz = 0f
        errorX = 0f
        errorY = 0f
        errorZ = 0f
        lastTime = Double.NaN
        pushed = false
        prevServerGap = Double.NaN
        serverGap = Double.NaN
        skippedGap = 0.0
        hadSkipBeforePush = false
        slack = 0.0
    }

    private fun interpolate(time: Double) {
        var step = 0
        while (step < count - 2 && times[at(step + 1)] <= time) step++
        val a = at(step)
        val b = at(step + 1)
        val span = times[b] - times[a]
        if (span <= 0.0 || isWarp(a, b, span)) {
            if (isWarp(a, b, span)) clearBlend()
            setFrom(if (time >= times[b]) b else a)
            clearVelocity()
            return
        }
        val f = ((time - times[a]) / span).toFloat().coerceIn(0f, 1f)
        val dx = xs[b] - xs[a]
        val dy = ys[b] - ys[a]
        val dz = zs[b] - zs[a]
        x = xs[a] + dx * f
        y = ys[a] + dy * f
        z = zs[a] + dz * f
        yaw = yaws[a] + shortestAngle(yaws[b] - yaws[a]) * f
        pitch = pitches[a] + (pitches[b] - pitches[a]) * f
        grounded = contacts[a]
        val inv = (1.0 / span).toFloat()
        velocityX = dx * inv
        velocityY = dy * inv
        velocityZ = dz * inv
    }

    private fun extrapolate(time: Double, newest: Int, wasUnderrun: Boolean, hadPush: Boolean) {
        if (!wasUnderrun || hadPush) {
            baseTime = times[newest]
            baseX = xs[newest]
            baseY = ys[newest]
            baseZ = zs[newest]
            computeVelocity(newest)
            clearBlend()
        }
        val elapsed = (time - baseTime).coerceAtLeast(0.0)
        val lead = min(elapsed, extrapolationSeconds).toFloat()
        x = baseX + extrapVx * lead
        y = baseY + extrapVy * lead
        z = baseZ + extrapVz * lead
        yaw = yaws[newest]
        pitch = pitches[newest]
        grounded = contacts[newest]
        if (elapsed < extrapolationSeconds) {
            velocityX = extrapVx
            velocityY = extrapVy
            velocityZ = extrapVz
        } else {
            clearVelocity()
        }
    }

    private fun computeVelocity(newest: Int) {
        extrapVx = 0f
        extrapVy = 0f
        extrapVz = 0f
        if (extrapolationSeconds <= 0.0 || count < 2) return
        val prev = at(count - 2)
        val span = times[newest] - times[prev]
        if (span <= 0.0 || isWarp(prev, newest, span)) return
        val inv = (1.0 / span).toFloat()
        extrapVx = (xs[newest] - xs[prev]) * inv
        extrapVy = if (contacts[newest]) 0f else (ys[newest] - ys[prev]) * inv
        extrapVz = (zs[newest] - zs[prev]) * inv
    }

    private fun startBlend(dx: Float, dy: Float, dz: Float) {
        if (distance(dx, dy, dz) > warpMetres) {
            clearBlend()
            return
        }
        errorX = dx
        errorY = dy
        errorZ = dz
    }

    private fun decayBlend(prevTime: Double, time: Double) {
        if (errorX == 0f && errorY == 0f && errorZ == 0f) return
        val dt = if (prevTime.isNaN()) 0.0 else (time - prevTime).coerceAtLeast(0.0)
        val keep = exp(-dt / blendSeconds).toFloat()
        errorX *= keep
        errorY *= keep
        errorZ *= keep
        if (distance(errorX, errorY, errorZ) < BLEND_EPSILON) {
            clearBlend()
        } else {
            x += errorX
            y += errorY
            z += errorZ
        }
    }

    private fun clearBlend() {
        errorX = 0f
        errorY = 0f
        errorZ = 0f
    }

    private fun clearVelocity() {
        velocityX = 0f
        velocityY = 0f
        velocityZ = 0f
    }

    private fun isWarp(a: Int, b: Int, span: Double): Boolean {
        val dist = distance(xs[b] - xs[a], ys[b] - ys[a], zs[b] - zs[a])
        return dist > warpMetres && dist / span > warpSpeedMetresPerSecond
    }

    private fun setFrom(index: Int) {
        x = xs[index]
        y = ys[index]
        z = zs[index]
        yaw = yaws[index]
        pitch = pitches[index]
        grounded = contacts[index]
    }

    private fun at(offset: Int): Int = (start + offset) % capacity

    private fun distance(dx: Float, dy: Float, dz: Float): Float = sqrt(dx * dx + dy * dy + dz * dz)

    private fun shortestAngle(radians: Float): Float {
        var turn = radians % TAU
        if (turn > PI_F) turn -= TAU
        if (turn < -PI_F) turn += TAU
        return turn
    }

    /** Default tuning constants for [RemotePoseBuffer]. */
    companion object {
        /** Default ring buffer capacity (32 snapshots). */
        const val DEFAULT_CAPACITY: Int = 32

        /** Default maximum velocity extrapolation lead past the newest snapshot (100 ms). */
        const val DEFAULT_EXTRAPOLATION_SECONDS: Double = 0.1

        /** Time constant over which post-underrun extrapolation error blends back onto the curve. */
        const val BLEND_SECONDS: Double = 0.1

        /** Minimum jump distance in metres before [WARP_METRES_PER_SECOND] classifies a warp. */
        const val WARP_METRES: Float = 5f

        /** Implied speed in metres per second above which a transition is treated as a teleport warp. */
        const val WARP_METRES_PER_SECOND: Double = 40.0

        private const val BLEND_EPSILON: Float = 1e-4f
        private const val MAX_CADENCE_INTERVALS: Int = 4
        private const val CADENCE_GROW_RATE: Double = 0.75
        private const val CADENCE_SHRINK_RATE: Double = 0.25
        private const val PI_F: Float = PI.toFloat()
        private const val TAU: Float = (2.0 * PI).toFloat()
    }
}
