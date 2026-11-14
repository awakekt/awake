/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.GpuDrawRequest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How far toward the light each caster reaches, measured once a frame along the direction every
 * cascade's depth row shares, so [reachingCasters] scans, in each cascade, only the casters that can
 * reach below its near plane (#1555).
 *
 * Cascades are fitted around one light, so each depth row is that direction scaled: a caster's
 * clip depth in a cascade is the cascade's offset plus its scale times the caster's reach. A caster
 * whose reach keeps it above the plane cannot lower it, so skipping its admission test changes
 * nothing; a cascade no caster can reach below keeps its plane without a scan. The cutoff carries
 * a margin over rounding, so every caster the full scan could count is still scanned. A cascade whose
 * depth row does not share the direction is scanned whole.
 *
 * Kept by [ScenePassScratch], so measuring allocates only when the caster count grows.
 */
internal class LightDepthReach {
    private val direction = FloatArray(AXES)
    private var reaches = FloatArray(0)

    /** The largest coordinate of any measured caster's bounds, which bounds the rounding of a depth. */
    private var extent = 0f

    /** The lowest reach of any measured caster, or +infinity with none. */
    var lowest: Float = Float.POSITIVE_INFINITY
        private set

    /**
     * Measures [casters] along [reference]'s depth row: opaque, casting, bounded ones, as the scan
     * counts them, and +infinity for the rest. False, measuring nothing, without a reference or a row.
     */
    fun measure(reference: Mat4?, casters: List<GpuDrawRequest>): Boolean {
        if (reference == null || !direction.setToDepthRowOf(reference)) return false
        if (reaches.size < casters.size) reaches = FloatArray(casters.size)
        lowest = Float.POSITIVE_INFINITY
        extent = 0f
        for (index in casters.indices) {
            val caster = casters[index]
            val box = caster.worldBounds.takeUnless { caster.transparent || !caster.castsShadows }
            reaches[index] = box?.lowestAlong(direction) ?: Float.POSITIVE_INFINITY
            if (box != null) {
                lowest = min(lowest, reaches[index])
                extent = max(extent, box.largestCoordinate())
            }
        }
        return true
    }

    /** Casters scanned since this was made: how many admission tests the cutoffs left. */
    var scanned: Long = 0
        private set

    /** Whether the caster at [index] in the last measured list reaches below [cutoff], counting it when it does. */
    fun scans(index: Int, cutoff: Float): Boolean = (reaches[index] < cutoff).also { if (it) scanned++ }

    /**
     * The reach a caster must fall below to reach [floor] under [viewProjection], with a margin over
     * rounding, or +infinity when its depth row does not share the measured direction.
     */
    fun cutoff(viewProjection: Mat4, floor: Float): Float {
        val m = viewProjection.data
        val x = m[DEPTH_ROW]
        val y = m[ROW_STRIDE + DEPTH_ROW]
        val z = m[2 * ROW_STRIDE + DEPTH_ROW]
        val scale = x * direction[0] + y * direction[1] + z * direction[2]
        val residual = abs(x - scale * direction[0]) + abs(y - scale * direction[1]) + abs(z - scale * direction[2])
        if (scale <= 0f || residual > SHARED_ROW_TOLERANCE * scale) return Float.POSITIVE_INFINITY
        val offset = m[TRANSLATION_COLUMN * ROW_STRIDE + DEPTH_ROW]
        // Rounding in either depth, and the row's own departure from the direction at the farthest corner.
        val margin = ROUNDING_MARGIN * (abs(offset) + abs(floor) + 1f + scale * extent) + residual * extent
        return (floor - offset + margin) / scale
    }

    /** Sets this to [matrix]'s depth row, normalized; false, leaving it, when the row is zero. */
    private fun FloatArray.setToDepthRowOf(matrix: Mat4): Boolean {
        val m = matrix.data
        val x = m[DEPTH_ROW]
        val y = m[ROW_STRIDE + DEPTH_ROW]
        val z = m[2 * ROW_STRIDE + DEPTH_ROW]
        val length = sqrt(x * x + y * y + z * z)
        if (length > 0f) {
            this[0] = x / length
            this[1] = y / length
            this[2] = z / length
        }
        return length > 0f
    }

    private fun Aabb.lowestAlong(d: FloatArray): Float =
        d[0] * (if (d[0] > 0f) min.x else max.x) +
            d[1] * (if (d[1] > 0f) min.y else max.y) +
            d[2] * (if (d[2] > 0f) min.z else max.z)

    private fun Aabb.largestCoordinate(): Float =
        maxOf(abs(min.x), abs(min.y), abs(min.z), abs(max.x), abs(max.y), abs(max.z))

    private companion object {
        const val AXES = 3

        /** How far a depth row may stray from the shared direction, relative to its length, and still share it. */
        const val SHARED_ROW_TOLERANCE = 1e-4f

        /** The margin, relative to the depths compared, by which a caster must clear the plane to be skipped. */
        const val ROUNDING_MARGIN = 1e-4f
    }
}
