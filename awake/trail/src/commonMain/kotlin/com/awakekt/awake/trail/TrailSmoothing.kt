/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.trail

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f

/**
 * The point at [t], from 0 to 1, on the uniform Catmull-Rom segment from [p1] to [p2], with [p0] and
 * [p3] the points before and after it, written into [out] and returned. The curve passes through
 * every key, so a path sampled once a frame stays on its samples.
 */
@Suppress("LongParameterList")
fun catmullRom(p0: Vec3f, p1: Vec3f, p2: Vec3f, p3: Vec3f, t: Float, out: Vec3f): Vec3f {
    val t2 = t * t
    val t3 = t2 * t
    // 0.5 * (2 p1 + (p2 - p0) t + (2 p0 - 5 p1 + 4 p2 - p3) t^2 + (3 p1 - p0 - 3 p2 + p3) t^3)
    val w0 = HALF * (-t3 + 2f * t2 - t)
    val w1 = HALF * (3f * t3 - 5f * t2 + 2f)
    val w2 = HALF * (-3f * t3 + 4f * t2 + t)
    val w3 = HALF * (t3 - t2)
    return out.set(
        w0 * p0.x + w1 * p1.x + w2 * p2.x + w3 * p3.x,
        w0 * p0.y + w1 * p1.y + w2 * p2.y + w3 * p3.y,
        w0 * p0.z + w1 * p1.z + w2 * p2.z + w3 * p3.z,
    )
}

/** How many pairs [TrailStrip.setSmoothed] writes for [keyCount] keys and [steps] steps between keys. */
fun smoothedPairCount(keyCount: Int, steps: Int): Int = if (keyCount < 2) keyCount else (keyCount - 1) * steps + 1

/**
 * Fills this strip from pair 0 with the Catmull-Rom curves through key pairs: [keysA] for the `a`
 * side and [keysB] for the `b` side, [steps] pairs from each key to the next and the last key itself,
 * so [smoothedPairCount] pairs in all. Past the first and last keys the path is continued in a
 * straight line, so the curve starts and ends on them heading the way the path does, and keys evenly
 * spaced along a line give evenly spaced pairs. Every pair takes [colorA] and [colorB]; [TrailStrip.visible]
 * is left as it is, for the caller to reveal the trail. Returns the pairs written.
 *
 * A path sampled at a few key frames (a blade's base and tip, a projectile's wings) becomes a smooth
 * ribbon of a few hundred pairs without the caller writing a spline. With a kept [scratch] it
 * allocates nothing.
 *
 * @throws IllegalArgumentException If the sides differ in length, [steps] is not positive, or the
 * pairs do not fit in [TrailStrip.capacity].
 */
@Suppress("LongParameterList")
fun TrailStrip.setSmoothed(keysA: List<Vec3f>, keysB: List<Vec3f>, steps: Int, colorA: Color, colorB: Color, scratch: SmoothingScratch = SmoothingScratch()): Int {
    require(keysA.size == keysB.size) { "Both sides need a key each: ${keysA.size} and ${keysB.size}." }
    require(steps > 0) { "steps must be positive, not $steps." }
    val pairs = smoothedPairCount(keysA.size, steps)
    require(pairs <= capacity) { "$pairs smoothed pairs do not fit in the trail's $capacity." }
    if (keysA.isEmpty()) return 0
    if (keysA.size == 1) {
        set(0, keysA[0], keysB[0], colorA, colorB)
        return 1
    }
    val last = keysA.size - 1
    var pair = 0
    for (key in 0 until last) {
        for (step in 0 until steps) {
            val t = step.toFloat() / steps
            point(keysA, key, last, t, scratch, scratch.a)
            point(keysB, key, last, t, scratch, scratch.b)
            set(pair++, scratch.a, scratch.b, colorA, colorB)
        }
    }
    set(pair++, keysA[last], keysB[last], colorA, colorB)
    return pair
}

/** Points [TrailStrip.setSmoothed] works in; keep one to smooth every frame without allocating. */
class SmoothingScratch {
    internal val a = Vec3f(0f, 0f, 0f)
    internal val b = Vec3f(0f, 0f, 0f)
    internal val before = Vec3f(0f, 0f, 0f)
    internal val after = Vec3f(0f, 0f, 0f)
}

/** The point at [t] between [key] and the next, with a key past either end continued in a line. */
@Suppress("LongParameterList")
private fun point(keys: List<Vec3f>, key: Int, last: Int, t: Float, scratch: SmoothingScratch, out: Vec3f): Vec3f {
    val p1 = keys[key]
    val p2 = keys[key + 1]
    val p0 = if (key > 0) keys[key - 1] else scratch.before.reflect(p1, p2)
    val p3 = if (key + 2 <= last) keys[key + 2] else scratch.after.reflect(p2, p1)
    return catmullRom(p0, p1, p2, p3, t, out)
}

/** Sets this to [through] mirrored by [away]: the point as far past [through] as [away] is before it. */
private fun Vec3f.reflect(through: Vec3f, away: Vec3f): Vec3f =
    set(2f * through.x - away.x, 2f * through.y - away.y, 2f * through.z - away.z)

private const val HALF = 0.5f
