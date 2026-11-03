/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

// Damping: closing the gap between a value and its target gradually over several frames,
// instead of jumping there in one, and doing it so the motion is the same at any frame rate.
//
// The tempting `value = lerp(value, target, 0.1f)` is NOT frame-rate independent: it closes 10%
// of the gap per *frame*, so a 144 Hz display converges more than twice as fast as a 60 Hz one,
// and a frame hitch visibly stalls it. Everything in this file takes the frame's elapsed time
// `dt` (seconds) and gives the same result whether a second is crossed in 30 steps or 240.
//
// Which one to use:
// - [damp] / [Vec3f.damp] / [dampAngle]: exponential smoothing. Starts fast, slows as it nears
//   the target, never overshoots, keeps no state beyond the value itself. The default choice:
//   follow cameras, UI fades, smoothing a noisy measurement.
// - [DampedFloat.smoothDamp] / [DampedVec3.smoothDamp]: a critically damped spring. It also
//   carries a velocity, so it eases *in* as well as out: no sudden start when the target jumps,
//   and a moving target is followed without lag building up. Use it where a visible kink at the
//   start of the motion would look wrong, and where a speed limit is wanted.
// - [ErrorOffset]: correction blending for netcode and reconciliation. The simulated position
//   jumps to the corrected value at once; the *drawn* position keeps a fading offset so the
//   viewer sees a glide instead of a pop.

/** `ln 2`: the rate that halves a gap in one second. */
private const val LN2 = 0.6931471805599453
private const val LN2_F = LN2.toFloat()
private const val PI_F = PI.toFloat()
private const val TWO_PI_F = (2.0 * PI).toFloat()

/** A spring's natural frequency is `SPRING_OMEGA_PER_SMOOTH_TIME / smoothTime`. */
private const val SPRING_OMEGA_PER_SMOOTH_TIME = 2f

/**
 * The fraction of the remaining gap that exponential damping closes in [dt] seconds, given a
 * [halfLife] (the time it takes to close half of any gap).
 *
 * Formula: `1 - 2^(-dt / halfLife)`. Use it to drive an interpolation that has no `damp` form
 * of its own, such as `Quat.slerp` or a colour blend: `slerp(current, target, dampingFraction(h, dt))`.
 *
 * @param halfLife Seconds to close half the gap. Zero or negative means "no smoothing": the
 *   result is 1 (snap to the target).
 * @param dt Seconds elapsed this frame. Zero or negative closes nothing: the result is 0.
 * @return A fraction in `0..1`.
 */
fun dampingFraction(halfLife: Float, dt: Float): Float = when {
    halfLife <= 0f -> 1f
    dt <= 0f -> 0f
    else -> 1f - exp(-LN2_F * dt / halfLife)
}

/**
 * Converts an exponential smoothing *rate* (per second) to the equivalent half-life.
 *
 * Hand-written smoothing is often `lerp(a, b, 1 - exp(-rate * dt))`; that is exactly
 * `damp(a, b, halfLifeForRate(rate), dt)`. A time constant `tau` (written `exp(-dt / tau)`) is the
 * rate `1 / tau`.
 *
 * @param rate Smoothing rate in 1/seconds; must be positive.
 * @return Seconds to close half the gap: `ln 2 / rate`.
 */
fun halfLifeForRate(rate: Float): Float = LN2_F / rate

/**
 * Moves [current] toward [target] by exponential damping and returns the new value.
 *
 * Half of the remaining gap is closed every [halfLife] seconds, so the result depends only on the
 * total time elapsed, not on how it was split into frames. It never overshoots the target.
 *
 * Pure: assign the result (`speed = damp(speed, targetSpeed, 0.1f, dt)`).
 *
 * @param current The value this frame starts from.
 * @param target The value being approached.
 * @param halfLife Seconds to close half the gap. Zero or negative snaps straight to [target].
 * @param dt Seconds elapsed this frame. Zero or negative leaves [current] unchanged.
 * @return The damped value.
 */
fun damp(current: Float, target: Float, halfLife: Float, dt: Float): Float = when {
    halfLife <= 0f -> target
    dt <= 0f -> current
    else -> current + (target - current) * (1f - exp(-LN2_F * dt / halfLife))
}

/**
 * Double-precision [damp]: moves [current] toward [target], closing half the gap every
 * [halfLife] seconds, the same at any frame rate.
 *
 * @param current The value this frame starts from.
 * @param target The value being approached.
 * @param halfLife Seconds to close half the gap. Zero or negative snaps straight to [target].
 * @param dt Seconds elapsed this frame. Zero or negative leaves [current] unchanged.
 * @return The damped value.
 */
fun damp(current: Double, target: Double, halfLife: Double, dt: Double): Double = when {
    halfLife <= 0.0 -> target
    dt <= 0.0 -> current
    else -> current + (target - current) * (1.0 - exp(-LN2 * dt / halfLife))
}

/**
 * Damps an angle in radians toward [targetRadians] along the shorter way round the circle.
 *
 * A plain [damp] from 350 degrees to 10 degrees would turn the long way, through 180. This one
 * goes the 20 degrees forward. The result is *not* wrapped into a range: it stays continuous with
 * [currentRadians] (so it can pass 2 pi), which keeps the next frame's call stable. Wrap it
 * yourself where a bounded angle is needed.
 *
 * @param currentRadians The angle this frame starts from, in radians.
 * @param targetRadians The angle being approached, in radians; any multiple of 2 pi away is the same.
 * @param halfLife Seconds to close half the angular gap. Zero or negative snaps to the target
 *   direction (expressed near [currentRadians]).
 * @param dt Seconds elapsed this frame. Zero or negative leaves the angle unchanged.
 * @return The damped angle, in radians.
 */
fun dampAngle(currentRadians: Float, targetRadians: Float, halfLife: Float, dt: Float): Float =
    currentRadians + shortestArc(targetRadians - currentRadians) * dampingFraction(halfLife, dt)

/** Wraps an angle difference into `-pi..pi`. */
private fun shortestArc(delta: Float): Float {
    val wrapped = delta % TWO_PI_F
    return when {
        wrapped > PI_F -> wrapped - TWO_PI_F
        wrapped < -PI_F -> wrapped + TWO_PI_F
        else -> wrapped
    }
}

/**
 * Moves this vector toward [target] by exponential damping, **in place**: half the remaining gap
 * closes every [halfLife] seconds, the same at any frame rate. Nothing is allocated.
 *
 * Follows the [Vec3f] naming contract: a bare verb, so it mutates `this` and returns it.
 *
 * @param target The point being approached; not modified.
 * @param halfLife Seconds to close half the gap. Zero or negative snaps to [target].
 * @param dt Seconds elapsed this frame. Zero or negative leaves this vector unchanged.
 * @return This vector, for chaining.
 */
fun Vec3f.damp(target: Vec3f, halfLife: Float, dt: Float): Vec3f = when {
    halfLife <= 0f -> set(target)
    dt <= 0f -> this
    else -> lerp(target, 1f - exp(-LN2_F * dt / halfLife))
}

// One axis of a critically damped spring, advanced by `dt` exactly. With `e` the distance from the
// target, the spring obeys `e'' = -w^2 e - 2 w e'`, whose exact solution over a step is
//   e(t) = (e0 + (v0 + w e0) t) exp(-w t)
//   v(t) = (v0 - w (v0 + w e0) t) exp(-w t)
// Being exact rather than an integration step, it gives the same result at any frame rate.
// `decay` is `exp(-w dt)`, computed once per call and shared by every axis.

private fun springError(error: Float, velocity: Float, omega: Float, dt: Float, decay: Float): Float =
    (error + (velocity + omega * error) * dt) * decay

private fun springVelocity(error: Float, velocity: Float, omega: Float, dt: Float, decay: Float): Float =
    (velocity - omega * (velocity + omega * error) * dt) * decay

/**
 * A float driven by a critically damped spring: a value and the velocity it is moving at.
 *
 * Unlike [damp], which starts at full speed the moment the target moves, a spring builds speed
 * up and bleeds it off, so motion eases in *and* out. "Critically damped" is the stiffest spring
 * that does not oscillate: it reaches the target as fast as possible without bouncing past it.
 *
 * Keep one instance per smoothed quantity (a field on a system or a component) and call
 * [smoothDamp] each frame; nothing is allocated per call.
 *
 * @property value The current value.
 * @property velocity The current rate of change, in units per second.
 */
class DampedFloat(var value: Float = 0f, var velocity: Float = 0f) {
    /**
     * Advances the spring toward [target] by [dt] seconds and returns the new [value].
     *
     * Starting at rest, about 60% of the gap is closed after [smoothTime] and about 90% after
     * twice that. The step is never allowed to carry the value past the target; if it would, the
     * value lands on the target and the velocity is cleared.
     *
     * @param target The value being approached.
     * @param smoothTime Roughly how long the approach takes, in seconds. Zero or negative snaps
     *   to [target] and clears the velocity.
     * @param dt Seconds elapsed this frame. Zero or negative changes nothing.
     * @param maxSpeed Upper bound on the speed, in units per second, both for this step's
     *   movement and for the [velocity] kept. Unlimited by default; negative counts as zero.
     * @return The new [value].
     */
    fun smoothDamp(
        target: Float,
        smoothTime: Float,
        dt: Float,
        maxSpeed: Float = Float.POSITIVE_INFINITY,
    ): Float = when {
        smoothTime <= 0f -> snapTo(target)
        dt <= 0f -> value
        else -> step(target, SPRING_OMEGA_PER_SMOOTH_TIME / smoothTime, dt, maxSpeed)
    }

    private fun step(target: Float, omega: Float, dt: Float, maxSpeed: Float): Float {
        val decay = exp(-omega * dt)
        val error = value - target
        var nextError = springError(error, velocity, omega, dt, decay)
        var nextVelocity = springVelocity(error, velocity, omega, dt, decay)
        if (maxSpeed < Float.POSITIVE_INFINITY) {
            val cap = max(maxSpeed, 0f)
            val maxStep = cap * dt
            nextError = error + (nextError - error).coerceIn(-maxStep, maxStep)
            nextVelocity = nextVelocity.coerceIn(-cap, cap)
        }
        // Never carry the value past the target in one step: land on it instead.
        if (error * nextError < 0f) return snapTo(target)
        value = target + nextError
        velocity = nextVelocity
        return value
    }

    /**
     * Puts the value on [target] with no velocity, for a teleport or a respawn.
     *
     * @return The new [value].
     */
    fun snapTo(target: Float): Float {
        value = target
        velocity = 0f
        return value
    }
}

/**
 * A point driven by a critically damped spring: the 3D form of [DampedFloat].
 *
 * Holds the [value] and [velocity] vectors it is given and mutates them in place, so passing an
 * existing vector (a camera's eye, say) damps that vector directly. Nothing is allocated per call.
 *
 * @property value The current point; mutated by [smoothDamp] and [snapTo].
 * @property velocity The current velocity, in units per second; mutated likewise.
 */
class DampedVec3(
    val value: Vec3f = Vec3f(0f, 0f, 0f),
    val velocity: Vec3f = Vec3f(0f, 0f, 0f),
) {
    /**
     * Advances the spring toward [target] by [dt] seconds, in place, and returns [value].
     *
     * The spring acts on each axis independently and exactly, so the path from rest is a straight
     * line and the result does not depend on the frame rate. As with [DampedFloat.smoothDamp], a
     * step that would carry the point past the target lands on it instead.
     *
     * @param target The point being approached; not modified.
     * @param smoothTime Roughly how long the approach takes, in seconds. Zero or negative snaps
     *   to [target] and clears the velocity.
     * @param dt Seconds elapsed this frame. Zero or negative changes nothing.
     * @param maxSpeed Upper bound on the speed (vector length), in units per second, both for this
     *   step's movement and for the [velocity] kept. Unlimited by default; negative counts as zero.
     * @return [value], for chaining.
     */
    fun smoothDamp(
        target: Vec3f,
        smoothTime: Float,
        dt: Float,
        maxSpeed: Float = Float.POSITIVE_INFINITY,
    ): Vec3f = when {
        smoothTime <= 0f -> snapTo(target)
        dt <= 0f -> value
        else -> step(target, SPRING_OMEGA_PER_SMOOTH_TIME / smoothTime, dt, maxSpeed)
    }

    private fun step(target: Vec3f, omega: Float, dt: Float, maxSpeed: Float): Vec3f {
        val decay = exp(-omega * dt)
        val ex = value.x - target.x
        val ey = value.y - target.y
        val ez = value.z - target.z
        var nx = springError(ex, velocity.x, omega, dt, decay)
        var ny = springError(ey, velocity.y, omega, dt, decay)
        var nz = springError(ez, velocity.z, omega, dt, decay)
        velocity.set(
            springVelocity(ex, velocity.x, omega, dt, decay),
            springVelocity(ey, velocity.y, omega, dt, decay),
            springVelocity(ez, velocity.z, omega, dt, decay),
        )
        if (maxSpeed < Float.POSITIVE_INFINITY) {
            val cap = max(maxSpeed, 0f)
            val maxStep = cap * dt
            val sx = nx - ex
            val sy = ny - ey
            val sz = nz - ez
            val step = sqrt(sx * sx + sy * sy + sz * sz)
            if (step > maxStep) {
                val k = maxStep / step
                nx = ex + sx * k
                ny = ey + sy * k
                nz = ez + sz * k
            }
            val speed = velocity.length3()
            if (speed > cap) velocity.scale(cap / speed)
        }
        // Never carry the point past the target in one step: land on it instead.
        return if (ex * nx + ey * ny + ez * nz < 0f) {
            snapTo(target)
        } else {
            value.set(target.x + nx, target.y + ny, target.z + nz)
        }
    }

    /**
     * Puts the point on [target] with no velocity, for a teleport or a respawn.
     *
     * @return [value], for chaining.
     */
    fun snapTo(target: Vec3f): Vec3f {
        velocity.set(0f, 0f, 0f)
        return value.set(target)
    }
}

/**
 * A positional correction that is applied to the simulation at once but shown gradually.
 *
 * Netcode and client-side prediction regularly learn that an entity is somewhere slightly
 * different from where it was drawn: a server snapshot, a reconcile after a mispredicted move.
 * Moving the drawn entity there in one frame is a visible pop. Instead:
 *
 * 1. Move the *simulated* position to the corrected one immediately, so gameplay is right.
 * 2. [add] the jump reversed (`old - new`) to this offset.
 * 3. Draw at `simulated + (x, y, z)`, and call [update] every frame so the offset fades.
 *
 * The viewer sees the entity glide from where it was to where it is. The offset halves every
 * [halfLife] seconds whatever the frame rate, further corrections accumulate on top of what is
 * still fading, and nothing is allocated per frame.
 *
 * @property halfLife Seconds for the offset to halve. Zero or negative clears it on the next
 *   [update], which shows corrections as an instant snap.
 */
class ErrorOffset(val halfLife: Float) {
    /** The offset still to be shown on the X axis. */
    var x: Float = 0f
        private set

    /** The offset still to be shown on the Y axis. */
    var y: Float = 0f
        private set

    /** The offset still to be shown on the Z axis. */
    var z: Float = 0f
        private set

    /**
     * Adds a correction to blend away: typically the old drawn position minus the corrected
     * simulated one. Accumulates with whatever is still fading.
     */
    fun add(dx: Float, dy: Float, dz: Float) {
        x += dx
        y += dy
        z += dz
    }

    /**
     * Fades the offset by [dt] seconds: it keeps `2^(-dt / halfLife)` of itself.
     *
     * @param dt Seconds elapsed this frame. Zero or negative changes nothing.
     */
    fun update(dt: Float) {
        if (halfLife <= 0f) {
            clear()
            return
        }
        if (dt <= 0f) return
        val keep = exp(-LN2_F * dt / halfLife)
        x *= keep
        y *= keep
        z *= keep
    }

    /**
     * Whether the offset is too small to see, so the caller can stop drawing it or [clear] it.
     *
     * @param epsilon Largest offset length still counted as settled, in world units.
     * @return `true` when the offset's length is at most [epsilon].
     */
    fun isSettled(epsilon: Float): Boolean = x * x + y * y + z * z <= epsilon * epsilon

    /** Drops the remaining offset, for a teleport where no blend is wanted. */
    fun clear() {
        x = 0f
        y = 0f
        z = 0f
    }
}
