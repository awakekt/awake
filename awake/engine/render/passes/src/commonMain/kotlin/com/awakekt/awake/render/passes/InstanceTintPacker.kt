/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Vec4

/** Packs optional per-instance RGBA multipliers, overwriting omitted tints with white on reuse. */
class InstanceTintPacker {
    private val packer = InstancePacker<Vec4>(GpuDataShape.Vec4, "InstanceTintBuffer") { out, offset, color ->
        out[offset] = color.x
        out[offset + 1] = color.y
        out[offset + 2] = color.z
        out[offset + 3] = color.w
    }

    /** Packs exactly [instanceCount] RGBA values; omitted colours become white, empty draws return null. */
    fun pack(colors: List<Vec4>?, instanceCount: Int, maxInstances: Int): FloatArray? {
        require(colors == null || colors.size == instanceCount) {
            "Instance tint count (${colors?.size}) must match instance count ($instanceCount)."
        }
        return packer.pack(instanceCount, maxInstances) { colors?.get(it) ?: WHITE }
    }

    private companion object {
        val WHITE = Vec4(1f, 1f, 1f, 1f)
    }
}
