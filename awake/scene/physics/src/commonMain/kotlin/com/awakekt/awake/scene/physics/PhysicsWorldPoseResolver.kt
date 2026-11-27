/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.ComponentStore
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Resolves a [Transform]'s world-space position and unit rotation quaternion without per-step
 * allocations, so [PhysicsSystem] and [PhysicsDebugWriter] always agree on where parented
 * colliders live.
 *
 * Unparented entities take a direct fast path (copying `position` and writing Euler angles into
 * `outRotation` with zero matrix builds). Parented entities compose their ancestor chain using the
 * exact same [Transform.computeWorldMatrix] rules as `TransformSystem`, then extract translation
 * and an orthonormalized rotation quaternion from the resulting world matrix.
 */
internal class PhysicsWorldPoseResolver {
    private val ancestors = ArrayList<Transform>(MAX_HIERARCHY_DEPTH)
    private val worldA = Mat4()
    private val worldB = Mat4()
    private val localScratch = Mat4()

    /**
     * Writes [transform]'s world-space translation into [outPosition] and unit rotation into
     * [outRotation]. Reads ancestors straight from [transformStore] (hoisted per step) so walking a
     * parent chain touches neither `KClass` nor `ComponentRegistry`.
     */
    fun resolve(
        transformStore: ComponentStore<Transform>?,
        transform: Transform,
        outPosition: Vec3f,
        outRotation: Quat,
    ) {
        if (transform.parent == null || transformStore == null) {
            outPosition.set(transform.position)
            setEulerInPlace(transform.rotation, outRotation)
            return
        }

        ancestors.clear()
        var cursor: Transform? = transform
        var depth = 0
        while (cursor != null && depth < MAX_HIERARCHY_DEPTH) {
            ancestors.add(cursor)
            val parentEntity = cursor.parent ?: break
            cursor = transformStore.get(parentEntity)
            depth++
        }

        var parentMat: Mat4? = null
        var currentMat = worldA
        var nextMat = worldB
        for (index in ancestors.lastIndex downTo 0) {
            ancestors[index].computeWorldMatrix(parentMat, currentMat, localScratch)
            parentMat = currentMat
            val swap = currentMat
            currentMat = nextMat
            nextMat = swap
        }
        val finalMat = parentMat ?: return
        outPosition.set(finalMat.m03, finalMat.m13, finalMat.m23)
        extractRotation(finalMat, outRotation)
    }

    /**
     * Writes [Quat.fromEuler] of [radians] into [out] without allocating intermediate quaternions:
     * matches `(X * Y) * Z` under [Quat.times] (Awake's swapped Hamilton product).
     */
    fun setEulerInPlace(radians: Vec3f, out: Quat) {
        val sx = sin(radians.x * HALF)
        val cx = cos(radians.x * HALF)
        val sy = sin(radians.y * HALF)
        val cy = cos(radians.y * HALF)
        val sz = sin(radians.z * HALF)
        val cz = cos(radians.z * HALF)
        val x = cy * sx
        val y = sy * cx
        val z = -sy * sx
        val w = cy * cx
        out.x = cz * x - sz * y
        out.y = cz * y + sz * x
        out.z = cz * z + sz * w
        out.w = cz * w - sz * z
    }

    private fun extractRotation(m: Mat4, out: Quat) {
        val sx = sqrt(m.m00 * m.m00 + m.m10 * m.m10 + m.m20 * m.m20)
        val sy = sqrt(m.m01 * m.m01 + m.m11 * m.m11 + m.m21 * m.m21)
        val sz = sqrt(m.m02 * m.m02 + m.m12 * m.m12 + m.m22 * m.m22)
        if (sx < DEGENERATE_SCALE || sy < DEGENERATE_SCALE || sz < DEGENERATE_SCALE) {
            out.set(Quat.IDENTITY)
            return
        }

        var r00 = m.m00 / sx
        var r10 = m.m10 / sx
        var r20 = m.m20 / sx
        val r01 = m.m01 / sy
        val r11 = m.m11 / sy
        val r21 = m.m21 / sy
        val r02 = m.m02 / sz
        val r12 = m.m12 / sz
        val r22 = m.m22 / sz

        // Flip one column when a mirrored parent scale produces a left-handed basis (det < 0).
        val det = r00 * (r11 * r22 - r12 * r21) -
            r01 * (r10 * r22 - r12 * r20) +
            r02 * (r10 * r21 - r11 * r20)
        if (det < 0f) {
            r00 = -r00
            r10 = -r10
            r20 = -r20
        }

        orthonormalBasisToQuat(r00, r01, r02, r10, r11, r12, r20, r21, r22, out)
    }

    @Suppress("LongParameterList")
    private fun orthonormalBasisToQuat(
        r00: Float,
        r01: Float,
        r02: Float,
        r10: Float,
        r11: Float,
        r12: Float,
        r20: Float,
        r21: Float,
        r22: Float,
        out: Quat,
    ) {
        val trace = r00 + r11 + r22
        when {
            trace > 0f -> {
                val s = sqrt(trace + 1f) * 2f
                out.w = QUARTER * s
                out.x = (r21 - r12) / s
                out.y = (r02 - r20) / s
                out.z = (r10 - r01) / s
            }
            r00 > r11 && r00 > r22 -> {
                val s = sqrt(1f + r00 - r11 - r22) * 2f
                out.w = (r21 - r12) / s
                out.x = QUARTER * s
                out.y = (r01 + r10) / s
                out.z = (r02 + r20) / s
            }
            r11 > r22 -> {
                val s = sqrt(1f + r11 - r00 - r22) * 2f
                out.w = (r02 - r20) / s
                out.x = (r01 + r10) / s
                out.y = QUARTER * s
                out.z = (r12 + r21) / s
            }
            else -> {
                val s = sqrt(1f + r22 - r00 - r11) * 2f
                out.w = (r10 - r01) / s
                out.x = (r02 + r20) / s
                out.y = (r12 + r21) / s
                out.z = QUARTER * s
            }
        }
        normalizeInPlace(out)
    }

    private fun normalizeInPlace(out: Quat) {
        val len = sqrt(out.x * out.x + out.y * out.y + out.z * out.z + out.w * out.w)
        if (len > DEGENERATE_SCALE) {
            out.x /= len
            out.y /= len
            out.z /= len
            out.w /= len
        } else {
            out.set(Quat.IDENTITY)
        }
    }

    private companion object {
        const val MAX_HIERARCHY_DEPTH = 64
        const val HALF = 0.5f
        const val QUARTER = 0.25f
        const val DEGENERATE_SCALE = 1e-6f
    }
}
