/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain

import com.awakekt.awake.core.math.Vec3f

/** One requested sample replacement in a [MutableHeightmap] edit batch. */
data class HeightmapSampleEdit(
    val x: Int,
    val z: Int,
    val height: Float,
)

/** Inclusive grid rectangle affected by one committed heightmap edit. */
data class HeightmapDirtyRegion(
    val minX: Int,
    val minZ: Int,
    val maxX: Int,
    val maxZ: Int,
) {
    init {
        require(minX <= maxX) { "HeightmapDirtyRegion minX must not exceed maxX." }
        require(minZ <= maxZ) { "HeightmapDirtyRegion minZ must not exceed maxZ." }
    }
}

/** The monotonic revision and smallest affected region from one successful edit batch. */
data class HeightmapChange(
    val revision: Long,
    val dirtyRegion: HeightmapDirtyRegion,
)

/**
 * An explicitly mutable heightmap for runtime editing.
 *
 * [apply] validates the whole batch before mutating any sample. A successful call returns the
 * smallest changed grid region, allowing independent mesh, collision, water, or placement
 * consumers to decide whether and how to refresh. This class owns no listener registry and does
 * not perform those refreshes itself.
 */
class MutableHeightmap(
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

    /** Increments once per committed batch; unchanged or rejected batches leave it untouched. */
    var revision: Long = 0L
        private set

    /** A defensive scale copy; [Vec3f] is mutable. */
    val scale: Vec3f get() = ownedScale.copy()

    fun heightAt(x: Int, z: Int): Float {
        requireCoordinates(x, z)
        return ownedSamples[z * width + x]
    }

    /** Replaces one height and reports its changed grid region, or `null` when unchanged. */
    fun setHeightAt(x: Int, z: Int, height: Float): HeightmapChange? =
        apply(listOf(HeightmapSampleEdit(x, z, height)))

    /**
     * Atomically applies [edits]. Invalid coordinates or non-finite values reject the entire
     * batch before any sample changes. Repeated edits to one sample are valid; the final value
     * wins and the dirty region still contains that sample once.
     */
    fun apply(edits: List<HeightmapSampleEdit>): HeightmapChange? {
        for (edit in edits) {
            requireCoordinates(edit.x, edit.z)
            require(edit.height.isFinite()) {
                "Heightmap edit at (${edit.x}, ${edit.z}) must be finite; was ${edit.height}."
            }
        }

        var minX = width
        var minZ = depth
        var maxX = -1
        var maxZ = -1
        for (edit in edits) {
            val index = edit.z * width + edit.x
            if (ownedSamples[index] == edit.height) continue
            ownedSamples[index] = edit.height
            minX = minOf(minX, edit.x)
            minZ = minOf(minZ, edit.z)
            maxX = maxOf(maxX, edit.x)
            maxZ = maxOf(maxZ, edit.z)
        }
        if (maxX < 0) return null

        check(revision != Long.MAX_VALUE) { "Heightmap revision overflow." }
        revision += 1L
        return HeightmapChange(revision, HeightmapDirtyRegion(minX, minZ, maxX, maxZ))
    }

    /** Returns an immutable snapshot for a consumer that needs a stable mesh or collider source. */
    fun snapshot(): Heightmap = Heightmap(ownedSamples, width, depth, ownedScale)

    /** Returns an owned copy for an explicit consumer such as a physics adapter. */
    fun copySamples(): FloatArray = ownedSamples.copyOf()

    private fun requireCoordinates(x: Int, z: Int) {
        require(x in 0 until width) { "Heightmap x must be in 0 until $width; was $x." }
        require(z in 0 until depth) { "Heightmap z must be in 0 until $depth; was $z." }
    }
}
