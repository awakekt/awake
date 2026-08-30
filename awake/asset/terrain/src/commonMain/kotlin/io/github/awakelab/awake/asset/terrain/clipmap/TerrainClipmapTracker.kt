/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.terrain.clipmap

import io.github.awakelab.awake.core.math.Vec3f
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
    /** World-space minimum $(X, Z)$ bound of this ring. */
    val minX: Float get() = snappedCenter.x - halfExtent
    val minZ: Float get() = snappedCenter.z - halfExtent

    /** World-space maximum $(X, Z)$ bound of this ring. */
    val maxX: Float get() = snappedCenter.x + halfExtent
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
 */
class TerrainClipmapTracker(
    val config: TerrainClipmapConfig = TerrainClipmapConfig(),
) {
    private val _ringStates = ArrayList<ClipmapRingState>(config.ringCount)
    val ringStates: List<ClipmapRingState> get() = _ringStates

    private var lastCameraPos: Vec3f = Vec3f(Float.NaN, Float.NaN, Float.NaN)

    init {
        update(Vec3f(0f, 0f, 0f))
    }

    /**
     * Updates all clipmap ring positions snapped to their respective grid intervals.
     *
     * To ensure that vertices always fall exactly on integer grid samples without shimmer,
     * ring $k$ is snapped to multiples of its grid spacing $S_k = \text{baseSpacing} \cdot 2^k$.
     *
     * @param cameraPosition World-space position of the primary observer/camera.
     * @return List of updated [ClipmapRingState] from Level 0 to Level ringCount - 1.
     */
    fun update(cameraPosition: Vec3f): List<ClipmapRingState> {
        _ringStates.clear()
        for (level in 0 until config.ringCount) {
            val spacing = config.spacingForLevel(level)
            val halfExtent = config.extentForLevel(level) * 0.5f

            // Snap camera (X, Z) to the discrete step of this LOD ring
            val snappedX = floor((cameraPosition.x / spacing) + 0.5f) * spacing
            val snappedZ = floor((cameraPosition.z / spacing) + 0.5f) * spacing

            _ringStates.add(
                ClipmapRingState(
                    level = level,
                    spacing = spacing,
                    snappedCenter = Vec3f(snappedX, 0f, snappedZ),
                    halfExtent = halfExtent,
                )
            )
        }
        lastCameraPos = cameraPosition
        return _ringStates
    }
}
