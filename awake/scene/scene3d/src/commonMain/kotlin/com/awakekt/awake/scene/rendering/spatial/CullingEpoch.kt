/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.spatial

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f

/**
 * Numbers the culling inputs that are not about one entity: the camera, the aspect, the shadow
 * volume and the size cutoff. The number changes whenever any of them does, so a verdict kept for
 * an entity that has not moved holds exactly while the number does.
 *
 * It never comes back to an earlier number, and 0 means this frame's verdicts must not be kept.
 */
internal class CullingEpoch {
    private val last = FloatArray(KEY_FLOATS)
    private val next = FloatArray(KEY_FLOATS)
    private var lastShadows = 0
    private var counter = 0

    /** The epoch for a frame, or 0 when [cacheable] is false (occluders, a probe) and nothing may be kept. */
    fun advance(lens: Lens, aspect: Float, shadowRevision: Int, minShadowCasterRatio: Float, indexed: Boolean, cacheable: Boolean): Int {
        write(lens, aspect, minShadowCasterRatio, indexed)
        var changed = !cacheable || shadowRevision != lastShadows
        for (i in 0 until KEY_FLOATS) {
            if (next[i] != last[i]) changed = true
            last[i] = next[i]
        }
        lastShadows = shadowRevision
        if (changed) counter++
        return if (cacheable) counter else 0
    }

    private fun write(lens: Lens, aspect: Float, ratio: Float, indexed: Boolean) {
        var i = putVec(0, lens.eye)
        i = putVec(i, lens.center)
        i = putVec(i, lens.up)
        next[i++] = lens.fovYRadians
        next[i++] = lens.near
        next[i++] = lens.far
        next[i++] = lens.projection.ordinal.toFloat()
        next[i++] = lens.orthoHalfHeight
        next[i++] = aspect
        next[i++] = ratio
        next[i] = if (indexed) 1f else 0f
    }

    private fun putVec(at: Int, v: Vec3f): Int {
        next[at] = v.x
        next[at + 1] = v.y
        next[at + 2] = v.z
        return at + 3
    }

    private companion object {
        const val KEY_FLOATS = 18
    }
}
