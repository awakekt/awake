/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.spatial

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Reusable zero-allocation scratch frustum (8 world-space corners and up to 6 inward-pointing unit
 * planes stored in primitive [FloatArray]s), reproducing `Frustum.corners` and `Frustum.planes`
 * without allocating `Vec3f`, `Plane`, or `List` objects on per-frame `SpatialGrid.queryFrustum`.
 */
internal class ScratchFrustum {
    private val corners = FloatArray(CORNER_COUNT * COORDS_PER_POINT)
    private val planes = FloatArray(MAX_PLANES * FLOATS_PER_PLANE)

    var minX: Float = Float.POSITIVE_INFINITY
        private set
    var maxX: Float = Float.NEGATIVE_INFINITY
        private set
    var minZ: Float = Float.POSITIVE_INFINITY
        private set
    var maxZ: Float = Float.NEGATIVE_INFINITY
        private set
    var planeCount: Int = 0
        private set

    fun set(lens: Lens, aspect: Float) {
        val fx = lens.center.x - lens.eye.x
        val fy = lens.center.y - lens.eye.y
        val fz = lens.center.z - lens.eye.z
        val fLen = sqrt(fx * fx + fy * fy + fz * fz)
        val invF = if (fLen > 0f) 1f / fLen else 0f
        val fwdX = fx * invF
        val fwdY = fy * invF
        val fwdZ = fz * invF

        // Mirror math.lookAtUp so straight-up / straight-down cameras never collapse to zero side vector.
        val up = lens.up
        val cx = fwdY * up.z - fwdZ * up.y
        val cy = fwdZ * up.x - fwdX * up.z
        val cz = fwdX * up.y - fwdY * up.x
        val upLenSq = up.x * up.x + up.y * up.y + up.z * up.z
        val useGivenUp = cx * cx + cy * cy + cz * cz > PARALLEL_EPSILON * upLenSq
        val refUpX = if (useGivenUp) up.x else 0f
        val refUpY = if (useGivenUp) {
            up.y
        } else if (abs(fwdY) > VERTICAL_LIMIT) {
            0f
        } else {
            1f
        }
        val refUpZ = if (useGivenUp) {
            up.z
        } else if (abs(fwdY) > VERTICAL_LIMIT) {
            1f
        } else {
            0f
        }

        val rx = fwdY * refUpZ - fwdZ * refUpY
        val ry = fwdZ * refUpX - fwdX * refUpZ
        val rz = fwdX * refUpY - fwdY * refUpX
        val rLen = sqrt(rx * rx + ry * ry + rz * rz)
        val invR = if (rLen > 0f) 1f / rLen else 0f
        val rightX = rx * invR
        val rightY = ry * invR
        val rightZ = rz * invR

        val upX = rightY * fwdZ - rightZ * fwdY
        val upY = rightZ * fwdX - rightX * fwdZ
        val upZ = rightX * fwdY - rightY * fwdX

        val ortho = lens.projection == Lens.Projection.Orthographic
        val tanHalf = if (ortho) 0f else tan(lens.fovYRadians * HALF)
        val nearHH = if (ortho) lens.orthoHalfHeight else tanHalf * lens.near
        val farHH = if (ortho) lens.orthoHalfHeight else tanHalf * lens.far
        writeNearFarQuads(lens, aspect, fwdX, fwdY, fwdZ, rightX, rightY, rightZ, upX, upY, upZ, nearHH, farHH)
        computeXzBounds()
        buildPlanes(aspect)
    }

    @Suppress("LongParameterList")
    private fun writeNearFarQuads(
        lens: Lens,
        aspect: Float,
        fwdX: Float,
        fwdY: Float,
        fwdZ: Float,
        rightX: Float,
        rightY: Float,
        rightZ: Float,
        upX: Float,
        upY: Float,
        upZ: Float,
        nearHH: Float,
        farHH: Float,
    ) {
        val nearHW = nearHH * aspect
        val farHW = farHH * aspect
        val eyeX = lens.eye.x
        val eyeY = lens.eye.y
        val eyeZ = lens.eye.z
        writeQuad(
            baseCorner = 0,
            cx = eyeX + fwdX * lens.near,
            cy = eyeY + fwdY * lens.near,
            cz = eyeZ + fwdZ * lens.near,
            rx = rightX * nearHW,
            ry = rightY * nearHW,
            rz = rightZ * nearHW,
            ux = upX * nearHH,
            uy = upY * nearHH,
            uz = upZ * nearHH,
        )
        writeQuad(
            baseCorner = NEAR_CORNER_COUNT,
            cx = eyeX + fwdX * lens.far,
            cy = eyeY + fwdY * lens.far,
            cz = eyeZ + fwdZ * lens.far,
            rx = rightX * farHW,
            ry = rightY * farHW,
            rz = rightZ * farHW,
            ux = upX * farHH,
            uy = upY * farHH,
            uz = upZ * farHH,
        )
    }

    fun intersects(box: Aabb): Boolean =
        intersects(box.min.x, box.min.y, box.min.z, box.max.x, box.max.y, box.max.z)

    @Suppress("LongParameterList")
    fun intersects(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float): Boolean {
        for (i in 0 until planeCount) {
            val base = i * FLOATS_PER_PLANE
            val nx = planes[base]
            val ny = planes[base + 1]
            val nz = planes[base + 2]
            val d = planes[base + 3]
            val px = if (nx >= 0f) maxX else minX
            val py = if (ny >= 0f) maxY else minY
            val pz = if (nz >= 0f) maxZ else minZ
            if (nx * px + ny * py + nz * pz + d < 0f) return false
        }
        return true
    }

    @Suppress("LongParameterList")
    private fun writeQuad(
        baseCorner: Int,
        cx: Float,
        cy: Float,
        cz: Float,
        rx: Float,
        ry: Float,
        rz: Float,
        ux: Float,
        uy: Float,
        uz: Float,
    ) {
        writeCorner(baseCorner + BOTTOM_LEFT, cx - rx - ux, cy - ry - uy, cz - rz - uz)
        writeCorner(baseCorner + BOTTOM_RIGHT, cx + rx - ux, cy + ry - uy, cz + rz - uz)
        writeCorner(baseCorner + TOP_RIGHT, cx + rx + ux, cy + ry + uy, cz + rz + uz)
        writeCorner(baseCorner + TOP_LEFT, cx - rx + ux, cy - ry + uy, cz - rz + uz)
    }

    private fun writeCorner(index: Int, x: Float, y: Float, z: Float) {
        val o = index * COORDS_PER_POINT
        corners[o] = x
        corners[o + 1] = y
        corners[o + 2] = z
    }

    private fun computeXzBounds() {
        var loX = Float.POSITIVE_INFINITY
        var hiX = Float.NEGATIVE_INFINITY
        var loZ = Float.POSITIVE_INFINITY
        var hiZ = Float.NEGATIVE_INFINITY
        for (i in 0 until CORNER_COUNT) {
            val o = i * COORDS_PER_POINT
            val x = corners[o]
            val z = corners[o + 2]
            loX = min(loX, x)
            hiX = max(hiX, x)
            loZ = min(loZ, z)
            hiZ = max(hiZ, z)
        }
        minX = loX
        maxX = hiX
        minZ = loZ
        maxZ = hiZ
    }

    private fun buildPlanes(aspect: Float) {
        planeCount = 0
        if (aspect.isNaN() || aspect <= 0f) return
        addPlane(BOTTOM_LEFT, BOTTOM_RIGHT, FAR_BOTTOM_RIGHT)
        addPlane(TOP_RIGHT, TOP_LEFT, FAR_TOP_RIGHT)
        addPlane(BOTTOM_LEFT, FAR_BOTTOM_LEFT, TOP_LEFT)
        addPlane(BOTTOM_RIGHT, TOP_RIGHT, FAR_TOP_RIGHT)
        addPlane(BOTTOM_LEFT, TOP_LEFT, TOP_RIGHT)
        addPlane(FAR_BOTTOM_LEFT, FAR_TOP_RIGHT, FAR_TOP_LEFT)
    }

    private fun addPlane(ia: Int, ib: Int, ic: Int) {
        val oa = ia * COORDS_PER_POINT
        val ob = ib * COORDS_PER_POINT
        val oc = ic * COORDS_PER_POINT
        val ax = corners[oa]
        val ay = corners[oa + 1]
        val az = corners[oa + 2]
        val abx = corners[ob] - ax
        val aby = corners[ob + 1] - ay
        val abz = corners[ob + 2] - az
        val acx = corners[oc] - ax
        val acy = corners[oc + 1] - ay
        val acz = corners[oc + 2] - az
        val nx = aby * acz - abz * acy
        val ny = abz * acx - abx * acz
        val nz = abx * acy - aby * acx
        val len = sqrt(nx * nx + ny * ny + nz * nz)
        if (len.isNaN() || len < DEGENERATE_NORMAL) return
        val inv = 1f / len
        val ux = nx * inv
        val uy = ny * inv
        val uz = nz * inv
        val base = planeCount * FLOATS_PER_PLANE
        planes[base] = ux
        planes[base + 1] = uy
        planes[base + 2] = uz
        planes[base + 3] = -(ux * ax + uy * ay + uz * az)
        planeCount++
    }

    private companion object {
        const val HALF = 0.5f
        const val CORNER_COUNT = 8
        const val NEAR_CORNER_COUNT = 4
        const val COORDS_PER_POINT = 3
        const val MAX_PLANES = 6
        const val FLOATS_PER_PLANE = 4
        const val BOTTOM_LEFT = 0
        const val BOTTOM_RIGHT = 1
        const val TOP_RIGHT = 2
        const val TOP_LEFT = 3
        const val FAR_BOTTOM_LEFT = 4
        const val FAR_BOTTOM_RIGHT = 5
        const val FAR_TOP_RIGHT = 6
        const val FAR_TOP_LEFT = 7
        const val DEGENERATE_NORMAL = 1e-6f
        const val PARALLEL_EPSILON = 1e-10f
        const val VERTICAL_LIMIT = 0.99f
    }
}
