/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain.clipmap

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * World-space runtime tracking state for a single Clipmap LOD ring.
 *
 * @property level Ring LOD level (0 for inner core, 1..N for outer annular rings).
 * @property spacing Grid spacing in meters for this level.
 * @property snappedCenter World-space $(X, Z)$ coordinate of the center of this ring.
 * @property halfExtent Half the world-space extent of this ring along each axis.
 */
data class ClipmapRingState(
    val level: Int,
    val spacing: Float,
    val snappedCenter: Vec3f,
    val halfExtent: Float,
) {
    /** World-space minimum X bound of this ring. */
    val minX: Float get() = snappedCenter.x - halfExtent

    /** World-space minimum Z bound of this ring. */
    val minZ: Float get() = snappedCenter.z - halfExtent

    /** World-space maximum X bound of this ring. */
    val maxX: Float get() = snappedCenter.x + halfExtent

    /** World-space maximum Z bound of this ring. */
    val maxZ: Float get() = snappedCenter.z + halfExtent

    /** Returns true if the world $(X, Z)$ position lies within this ring's bounding area. */
    fun contains(x: Float, z: Float): Boolean =
        x in minX..maxX && z in minZ..maxZ

    /**
     * Computes the continuous morph factor $\alpha \in [0, 1]$ for a vertex at $(x, z)$.
     *
     * In the inner $(1 - \text{morphWidth})$ of the ring, $\alpha = 0.0$ (no morph).
     * In the outer perimeter band, $\alpha$ scales linearly from $0.0 \to 1.0$ at the ring edge,
     * allowing the shader to smoothly snap vertex positions to the coarser parent grid.
     */
    fun computeMorphFactor(x: Float, z: Float, morphWidth: Float = 0.25f): Float {
        val dx = abs(x - snappedCenter.x) / halfExtent
        val dz = abs(z - snappedCenter.z) / halfExtent
        val normalizedDist = max(dx, dz) // Chebyshev / square distance
        val morphStart = 1.0f - morphWidth.coerceIn(0.05f, 0.5f)
        if (normalizedDist <= morphStart) return 0.0f
        return ((normalizedDist - morphStart) / (1.0f - morphStart)).coerceIn(0.0f, 1.0f)
    }
}

/**
 * Tracks viewer / camera movement and computes discrete grid-snapped origins for all clipmap rings.
 *
 * @property config Configuration parameters for clipmap rings and extents.
 * @param terrain The heightmap the rings cover. When set, they centre on the camera clamped into
 *   its rectangle, so a camera off the map, zoomed out or outside it, still has the map in range.
 */
class TerrainClipmapTracker(
    /** Configuration parameters for clipmap rings and extents. */
    val config: TerrainClipmapConfig = TerrainClipmapConfig(),
    private val terrain: Heightmap? = null,
) {
    private val _ringStates = ArrayList<ClipmapRingState>(config.ringCount)

    /** Current world-space states for all concentric clipmap rings in inner-to-outer order. */
    val ringStates: List<ClipmapRingState> get() = _ringStates

    private var lastCameraPos: Vec3f = Vec3f(Float.NaN, Float.NaN, Float.NaN)

    init {
        update(Vec3f(0f, 0f, 0f))
    }

    /**
     * Updates all clipmap ring positions snapped to their respective grid intervals.
     *
     * Each level's corner snaps to the next level's grid, twice its own spacing, not just to its
     * own: then its border lies on that coarser level's grid lines, and the two meet vertex for
     * vertex. Snapping only to its own spacing left the border half a coarse cell off, which is a
     * crack.
     *
     * @param cameraPosition World-space position of the primary observer/camera.
     * @return List of updated [ClipmapRingState] from Level 0 to Level ringCount - 1.
     */
    fun update(cameraPosition: Vec3f): List<ClipmapRingState> {
        val outerHalf = config.extentForLevel(config.ringCount - 1) * 0.5f
        val outerSnapMargin = config.spacingForLevel(config.ringCount - 1)
        val reach = outerHalf - outerSnapMargin
        val focusX = terrain?.let { clampFocus(cameraPosition.x, it.minX, it.minX + it.halfExtentX * 2f, reach) } ?: cameraPosition.x
        val focusZ = terrain?.let { clampFocus(cameraPosition.z, it.minZ, it.minZ + it.halfExtentZ * 2f, reach) } ?: cameraPosition.z
        for (level in 0 until config.ringCount) {
            val spacing = config.spacingForLevel(level)
            val halfExtent = config.extentForLevel(level) * 0.5f

            val coarse = spacing * 2f
            val snappedX = floor((focusX - halfExtent) / coarse + 0.5f) * coarse + halfExtent
            val snappedZ = floor((focusZ - halfExtent) / coarse + 0.5f) * coarse + halfExtent

            if (level < _ringStates.size) {
                val existing = _ringStates[level]
                if (existing.snappedCenter.x == snappedX && existing.snappedCenter.z == snappedZ) {
                    continue
                }
                _ringStates[level] = ClipmapRingState(
                    level = level,
                    spacing = spacing,
                    snappedCenter = Vec3f(snappedX, 0f, snappedZ),
                    halfExtent = halfExtent,
                )
            } else {
                _ringStates.add(
                    ClipmapRingState(
                        level = level,
                        spacing = spacing,
                        snappedCenter = Vec3f(snappedX, 0f, snappedZ),
                        halfExtent = halfExtent,
                    ),
                )
            }
        }
        lastCameraPos = cameraPosition
        return _ringStates
    }

    /**
     * Leaves [position] unclamped across the terrain and outside it while the outermost ring still
     * covers the whole map ([reach]), so adjoining terrains share identical ring centres and LOD
     * transitions across their shared border. Only clamps once the camera leaves far enough away
     * that the far side of the map would otherwise fall outside the outermost ring.
     */
    private fun clampFocus(position: Float, min: Float, max: Float, reach: Float): Float {
        val lo = minOf(min, max - reach)
        val hi = maxOf(max, min + reach)
        return position.coerceIn(lo, hi)
    }
}
