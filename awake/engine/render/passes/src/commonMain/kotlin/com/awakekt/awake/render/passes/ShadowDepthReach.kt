/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuShadowCascadeData
import kotlin.math.abs
import kotlin.math.min

/**
 * These cascades with each one's near plane pulled back toward its light until it holds every
 * caster in [casters] that can write a texel of its map: the fallback for a backend whose shadow
 * depth pass cannot clamp depth.
 *
 * A cascade's depth range reaches only a fixed margin toward the light (see [cascadeShadowBoxes]),
 * but [ShadowCasterVolume] admits casters without limit in that direction, so a tall caster far
 * toward a low sun can be admitted and then clipped by the rasterizer at the near plane, losing
 * its shadow. A backend that clamps depth in the shadow pass flattens such a caster onto the near
 * plane instead, which is exact for every receiver the cascade holds, and needs none of this.
 *
 * Only the depth row of each matrix changes, and the far plane stays where it was, so the map's
 * footprint, its texel snapping and every casting plane are untouched: [ShadowCasterVolume] built
 * from the fitted matrices still describes these. The depth scale the receiver bias converts
 * through is rescaled to match, so that bias keeps its world-space size. What is lost is depth
 * precision, in proportion to how far the plane moves, and only on frames where a caster reaches
 * past it.
 *
 * A caster counts when it is opaque, casts ([GpuDrawRequest.castsShadows]), has
 * [GpuDrawRequest.worldBounds], and those bounds lie at least partly inside the cascade's side and
 * far planes. One without bounds cannot be measured
 * and leaves the plane where it is. A cascade whose matrix is not orthographic is returned as it
 * was.
 *
 * @return this when no cascade moved, otherwise a copy with the moved cascades replaced.
 */
internal fun GpuShadowCascadeData.reachingCasters(
    casters: List<GpuDrawRequest>,
    clipSpace: ClipSpace,
): GpuShadowCascadeData {
    val nearDepth = if (clipSpace.depthZeroToOne) 0f else -1f
    val planes = FloatArray(CASTING_PLANE_FLOATS)
    var pulled: MutableList<Mat4>? = null
    var scales: FloatArray? = null
    viewProjections.forEachIndexed { cascade, viewProjection ->
        if (!viewProjection.isOrthographic()) return@forEachIndexed
        writeCastingPlanes(viewProjection, planes, 0)
        val lowest = casters.lowestDepthIn(viewProjection, planes, nearDepth)
        if (lowest >= nearDepth) return@forEachIndexed
        // A little past the farthest caster, so rounding cannot clip the corner that set the plane.
        val low = lowest - (nearDepth - lowest) * REACH_MARGIN
        val squeeze = (FAR_DEPTH - nearDepth) / (FAR_DEPTH - low)
        (pulled ?: viewProjections.toMutableList().also { pulled = it })[cascade] =
            viewProjection.withDepthRemapped(low, nearDepth, squeeze)
        (scales ?: depthScales.copyOf().also { scales = it })[cascade] = depthScales[cascade] * squeeze
    }
    val movedProjections = pulled ?: return this
    return GpuShadowCascadeData(
        viewProjections = movedProjections,
        splitDistances = splitDistances,
        depthScales = scales ?: depthScales,
        worldExtents = worldExtents,
        blendStartDistances = blendStartDistances,
    )
}

/**
 * The smallest clip-space depth under [viewProjection] of any opaque, bounded caster that casts and
 * that the casting [planes] admit, or [floor] when none reaches below it.
 */
private fun List<GpuDrawRequest>.lowestDepthIn(viewProjection: Mat4, planes: FloatArray, floor: Float): Float {
    var lowest = floor
    for (index in indices) {
        val caster = this[index]
        val box = caster.worldBounds.takeUnless { caster.transparent || !caster.castsShadows } ?: continue
        if (planes.admitsBox(0, box.min.x, box.min.y, box.min.z, box.max.x, box.max.y, box.max.z)) {
            val depth = viewProjection.lowestDepth(box)
            if (depth.isFinite()) lowest = min(lowest, depth)
        }
    }
    return lowest
}

/** A copy of this matrix whose depth row maps clip depth [low] to [nearDepth] and leaves 1 at 1. */
private fun Mat4.withDepthRemapped(low: Float, nearDepth: Float, squeeze: Float): Mat4 {
    val moved = Mat4().set(this)
    for (column in 0 until TRANSLATION_COLUMN) moved.data[column * ROW_STRIDE + DEPTH_ROW] *= squeeze
    moved.data[TRANSLATION_COLUMN * ROW_STRIDE + DEPTH_ROW] =
        nearDepth + (data[TRANSLATION_COLUMN * ROW_STRIDE + DEPTH_ROW] - low) * squeeze
    return moved
}

/** The smallest clip-space depth any corner of [box] reaches under this affine matrix. */
private fun Mat4.lowestDepth(box: Aabb): Float {
    val m = data
    val x = m[DEPTH_ROW]
    val y = m[ROW_STRIDE + DEPTH_ROW]
    val z = m[2 * ROW_STRIDE + DEPTH_ROW]
    return m[TRANSLATION_COLUMN * ROW_STRIDE + DEPTH_ROW] +
        x * (if (x > 0f) box.min.x else box.max.x) +
        y * (if (y > 0f) box.min.y else box.max.y) +
        z * (if (z > 0f) box.min.z else box.max.z)
}

/** Whether this matrix leaves `w` at 1, as an orthographic projection does: only then is depth linear in position. */
private fun Mat4.isOrthographic(): Boolean =
    abs(data[W_ROW]) < AFFINE_TOLERANCE &&
        abs(data[ROW_STRIDE + W_ROW]) < AFFINE_TOLERANCE &&
        abs(data[2 * ROW_STRIDE + W_ROW]) < AFFINE_TOLERANCE &&
        abs(data[TRANSLATION_COLUMN * ROW_STRIDE + W_ROW] - 1f) < AFFINE_TOLERANCE

private const val ROW_STRIDE = 4
private const val DEPTH_ROW = 2
private const val W_ROW = 3
private const val TRANSLATION_COLUMN = 3

/** Clip-space depth of the far plane, in both depth conventions. */
private const val FAR_DEPTH = 1f

/** How far past the farthest caster the moved plane sits, as a fraction of the distance moved. */
private const val REACH_MARGIN = 0.01f

private const val AFFINE_TOLERANCE = 1e-6f
