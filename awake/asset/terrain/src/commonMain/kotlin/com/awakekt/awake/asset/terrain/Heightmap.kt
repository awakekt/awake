/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain

import com.awakekt.awake.core.math.GridOrigin
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.floor

/**
 * Immutable rectangular terrain samples in row-major `z * width + x` order.
 *
 * Coordinates are **centred**: the map spans `-halfExtentX..halfExtentX` on X and the same on Z,
 * so sample `(x, z)` is at `(x * scale.x - halfExtentX, sample * scale.y, z * scale.z - halfExtentZ)`.
 * The origin is the middle of the map, matching `generate { cube() }` and `plane()`, and matching
 * what a `Transform.position` means everywhere else: where the thing IS, not where its corner is.
 * A corner origin put a 256m tile's position 181m from anything visible, which is measured by LOD
 * distance, culling spheres and streaming alike.
 *
 * Content authored against the older corner convention passes [GridOrigin.Corner], which restores
 * `(x * scale.x, _, z * scale.z)` exactly. The choice belongs here rather than at each consumer:
 * the mesh builder, the navigation bake and `heightAtWorld` all read it from the map, so they
 * cannot end up disagreeing by half a map.
 *
 * The constructor takes ownership of both mutable inputs, so later caller mutations cannot
 * desynchronise a rendered mesh and a collider.
 */
class Heightmap(
    samples: FloatArray,
    val width: Int,
    val depth: Int,
    scale: Vec3f,
    val origin: GridOrigin = GridOrigin.Centered,
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

    /** Half the world width. */
    val halfExtentX: Float get() = (width - 1) * ownedScale.x * HALF

    /** Half the world depth. */
    val halfExtentZ: Float get() = (depth - 1) * ownedScale.z * HALF

    /** World X of sample column 0: `-halfExtentX` when centred, zero when corner-anchored. */
    val minX: Float get() = if (origin == GridOrigin.Centered) -halfExtentX else 0f

    /** World Z of sample row 0. */
    val minZ: Float get() = if (origin == GridOrigin.Centered) -halfExtentZ else 0f

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
        val gridX = (worldX - minX) / ownedScale.x
        val gridZ = (worldZ - minZ) / ownedScale.z
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

    /** The same samples read under a different [GridOrigin]. */
    fun withOrigin(origin: GridOrigin): Heightmap =
        Heightmap(ownedSamples, width, depth, ownedScale, origin)
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
private const val HALF = 0.5f
