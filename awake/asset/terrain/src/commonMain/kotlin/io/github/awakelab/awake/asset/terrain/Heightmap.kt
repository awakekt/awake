/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.terrain

import io.github.awakelab.awake.core.math.Vec3f
import kotlin.math.floor

/**
 * Immutable rectangular terrain samples in row-major `z * width + x` order.
 *
 * Coordinates use a corner origin: sample `(x, z)` is at
 * `(x * scale.x, sample * scale.y, z * scale.z)`. The constructor takes ownership of both
 * mutable inputs, so later caller mutations cannot desynchronise a rendered mesh and a collider.
 */
class Heightmap(
    samples: FloatArray,
    val width: Int,
    val depth: Int,
    scale: Vec3f,
) {
    private val ownedSamples: FloatArray
    private val ownedScale: Vec3f

    init {
        validateHeightmap(samples, width, depth, scale)
        ownedSamples = samples.copyOf()
        ownedScale = scale.copy()
    }

    /** A defensive scale copy; [Vec3f] is mutable. */
    val scale: Vec3f get() = ownedScale.copy()

    /** Returns the height sample at [x], [z], rejecting out-of-range coordinates. */
    fun heightAt(x: Int, z: Int): Float {
        require(x in 0 until width) { "Heightmap x must be in 0 until $width; was $x." }
        require(z in 0 until depth) { "Heightmap z must be in 0 until $depth; was $z." }
        return ownedSamples[z * width + x]
    }

    /**
     * Returns the bilinearly interpolated height at world-space [worldX], [worldZ], or
     * [Float.NaN] when the position lies outside the map.
     *
     * Unlike [heightAt], which returns the raw sample, this applies `scale.y` — the result is a
     * world Y, matching the coordinate convention documented on this class.
     *
     * NaN rather than a nullable `Float`: a navigation bake calls this once per navigation cell,
     * hundreds of thousands of times per world cell, and a boxed `Float?` at that rate is exactly
     * the garbage the engine's per-frame allocation rule exists to prevent. Callers test
     * [Float.isNaN].
     */
    fun heightAtWorld(worldX: Float, worldZ: Float): Float {
        val gridX = worldX / ownedScale.x
        val gridZ = worldZ / ownedScale.z
        // Written as a positive range test so a NaN argument fails it. The negated form would
        // pass NaN through to floor(), whose Int conversion silently yields 0.
        val insideX = gridX >= 0f && gridX <= (width - 1).toFloat()
        val insideZ = gridZ >= 0f && gridZ <= (depth - 1).toFloat()
        if (!insideX || !insideZ) return Float.NaN

        val x0 = floor(gridX).toInt()
        val z0 = floor(gridZ).toInt()
        // Clamped rather than +1: a query exactly on the far edge has no next sample to blend
        // with, and its fraction is zero, so the clamped neighbour contributes nothing.
        val x1 = if (x0 + 1 < width) x0 + 1 else x0
        val z1 = if (z0 + 1 < depth) z0 + 1 else z0
        val fx = gridX - x0
        val fz = gridZ - z0

        val near = ownedSamples[z0 * width + x0].let { it + (ownedSamples[z0 * width + x1] - it) * fx }
        val far = ownedSamples[z1 * width + x0].let { it + (ownedSamples[z1 * width + x1] - it) * fx }
        return (near + (far - near) * fz) * ownedScale.y
    }

    /** Returns an owned copy for an explicit consumer such as a physics adapter. */
    fun copySamples(): FloatArray = ownedSamples.copyOf()

    /** Creates an independently editable heightmap; edits never mutate this immutable asset. */
    fun mutableCopy(): MutableHeightmap = MutableHeightmap(ownedSamples, width, depth, ownedScale)
}

internal fun validateHeightmap(samples: FloatArray, width: Int, depth: Int, scale: Vec3f) {
    require(width >= MIN_AXIS_SAMPLES) { "Heightmap width must be at least $MIN_AXIS_SAMPLES; was $width." }
    require(depth >= MIN_AXIS_SAMPLES) { "Heightmap depth must be at least $MIN_AXIS_SAMPLES; was $depth." }
    require(width.toLong() * depth == samples.size.toLong()) {
        "Heightmap samples must contain width * depth values; was ${samples.size} for $width x $depth."
    }
    require(scale.x.isFinite() && scale.y.isFinite() && scale.z.isFinite()) {
        "Heightmap scale must be finite; was $scale."
    }
    require(scale.x > 0f && scale.y > 0f && scale.z > 0f) {
        "Heightmap scale must be positive; was $scale."
    }
    require(samples.all(Float::isFinite)) { "Heightmap samples must be finite." }
}

private const val MIN_AXIS_SAMPLES = 2
