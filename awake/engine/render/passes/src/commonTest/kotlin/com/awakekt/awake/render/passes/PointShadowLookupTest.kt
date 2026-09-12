/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.math.transformPosition
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PointShadowLookupTest {
    @Test
    fun selectsEachFaceAndKeepsTheAxisCentreAtUvHalf() {
        val directions = listOf(
            Vec3f.RIGHT,
            Vec3f.LEFT,
            Vec3f.UP,
            Vec3f.DOWN,
            Vec3f.BACK,
            Vec3f.FORWARD,
        )
        directions.forEachIndexed { face, direction ->
            val lookup = pointShadowLookup(direction)
            assertEquals(face, lookup.face, "direction=$direction")
            assertEquals(0.5f, lookup.uv.x)
            assertEquals(0.5f, lookup.uv.y)
            assertEquals(1f, lookup.uv.z)
        }
    }

    @Test
    fun rejectsAZeroDirection() {
        assertFailsWith<IllegalArgumentException> { pointShadowLookup(Vec3f.ZERO) }
    }

    @Test
    fun uvMatchesTheFaceMatricesOnBothBackendClipSpaces() {
        val directions = listOf(
            Vec3f(1f, 0.25f, 0.5f),
            Vec3f(-1f, 0.25f, 0.5f),
            Vec3f(0.25f, 1f, 0.5f),
            Vec3f(0.25f, -1f, 0.5f),
            Vec3f(0.25f, 0.5f, 1f),
            Vec3f(0.25f, 0.5f, -1f),
        )
        val mismatches = mutableListOf<String>()
        for (clipSpace in listOf(ClipSpace.WebGpu, ClipSpace.Vulkan)) {
            val matrices = pointShadowMatrices(Vec3f.ZERO, 10f, clipSpace).viewProjections
            directions.forEachIndexed { face, direction ->
                val lookup = pointShadowLookup(direction)
                val clip = matrices[face].transformPosition(Vec4(direction.x, direction.y, direction.z, 1f))
                val ndcX = clip.x / clip.w
                val ndcY = clip.y / clip.w
                val expectedU = (ndcX + 1f) * 0.5f
                val expectedV = if (clipSpace.flipY) {
                    (ndcY + 1f) * 0.5f
                } else {
                    (1f - ndcY) * 0.5f
                }
                if (face != lookup.face ||
                    kotlin.math.abs(expectedU - lookup.uv.x) > 0.0001f ||
                    kotlin.math.abs(expectedV - lookup.uv.y) > 0.0001f
                ) {
                    mismatches += "$clipSpace face=$face lookup=${lookup.uv} expected=($expectedU,$expectedV) clip=$clip"
                }
            }
        }
        assertTrue(mismatches.isEmpty(), mismatches.joinToString("\n"))
    }

    @Test
    fun comparisonDepthMatchesThePerspectiveProjectionOnBothBackendClipSpaces() {
        val near = 0.05f
        val range = 10f
        val directions = listOf(
            Vec3f(1f, 0.25f, 0.5f),
            Vec3f(-1f, 0.25f, 0.5f),
            Vec3f(0.25f, 1f, 0.5f),
            Vec3f(0.25f, -1f, 0.5f),
            Vec3f(0.25f, 0.5f, 1f),
            Vec3f(0.25f, 0.5f, -1f),
        )
        val mismatches = mutableListOf<String>()
        for (clipSpace in listOf(ClipSpace.WebGpu, ClipSpace.Vulkan)) {
            val matrices = pointShadowMatrices(Vec3f.ZERO, range, clipSpace).viewProjections
            directions.forEach { direction ->
                val lookup = pointShadowLookup(direction)
                val clip = matrices[lookup.face].transformPosition(
                    Vec4(direction.x, direction.y, direction.z, 1f),
                )
                val projectedDepth = clip.z / clip.w
                val expectedDepth = range / (range - near) -
                    near * range / ((range - near) * lookup.uv.z)
                val depth = if (clipSpace.depthZeroToOne) {
                    expectedDepth
                } else {
                    expectedDepth * 0.5f + 0.5f
                }
                if (abs(projectedDepth - depth) > 0.0001f) {
                    mismatches += "$clipSpace direction=$direction projected=$projectedDepth expected=$depth"
                }
            }
        }
        assertTrue(mismatches.isEmpty(), mismatches.joinToString("\n"))
    }

    /**
     * A fixed NDC bias becomes a radial gap under point-light perspective depth.
     *
     * At a 1.6m receiver, the old 0.002 subtraction moves the comparison surface by about 0.096m
     * for a 6m light range. The texel-scaled receiver bias used by the shader stays below 0.02m
     * for the same 2048px map and a moderately angled receiver. This is the unit-level negative
     * control for the peter-panning symptom; the Vulkan contact probe checks the resulting pixels.
     */
    @Test
    fun fixedPointShadowNdcBiasBecomesAWorldSpaceGap() {
        val near = 0.05f
        val range = 6f
        val receiverMajor = 1.6f
        val fixedNdcBias = 0.002f
        val mapSize = 2048f
        val nDotL = 0.8f

        fun depthAt(major: Float): Float =
            range / (range - near) - near * range / ((range - near) * major)

        fun majorAt(depth: Float): Float =
            near * range / ((range - near) * (range / (range - near) - depth))

        val fixedGap = receiverMajor - majorAt(depthAt(receiverMajor) - fixedNdcBias)
        val slopeScale = sqrt(1f - nDotL * nDotL) / nDotL
        val texelWorld = 2f * receiverMajor / mapSize
        val worldBias = texelWorld * (1.5f + 2f * slopeScale)
        val depthBias = near * range / ((range - near) * receiverMajor * receiverMajor) * worldBias
        val scaledGap = receiverMajor - majorAt(depthAt(receiverMajor) - depthBias)

        assertTrue(fixedGap > 0.08f, "fixed NDC bias only moved $fixedGap m")
        assertTrue(scaledGap < 0.02f, "texel-scaled bias moved $scaledGap m")
    }
}
