/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.debug

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Vec3f

/**
 * The `culling` debug source: each mesh's bounds, coloured by what culling decided last frame, and
 * the counts.
 *
 * - **Green:** drawn.
 * - **Grey:** outside the camera's view.
 * - **Red:** in view, but hidden behind an occluder.
 * - **Violet:** rejected by the camera, but still drawn into the shadow maps.
 * - **Cyan:** a copy of an instanced mesh, drawn every frame without being culled.
 *
 * While [CullingProbe.frozen] is on, the frozen view's frustum is drawn in yellow.
 *
 * It reads [probe], which a [com.awakekt.awake.scene.rendering.RenderSystem3D] fills while the probe
 * is its `cullingProbe`. A scene app on the default systems does that by itself when `culling` is
 * on, and F9 freezes culling there.
 */
class CullingDebugSource(
    /** What the render system recorded. */
    val probe: CullingProbe,
) : DebugSource {
    override val id: String = ID
    override val title: String = "Culling"

    private val corners = Array(BOX_CORNERS) { Vec3f(0f, 0f, 0f) }
    private val low = Vec3f(0f, 0f, 0f)
    private val high = Vec3f(0f, 0f, 0f)

    override fun rows(out: DebugRows) {
        out.row("drawn", probe.drawn)
        out.row("outside view", probe.outsideView)
        out.row("occluded", probe.occluded)
        out.row("shadow only", probe.shadowOnly)
        if (probe.notCulled > 0) out.row("not culled (instanced)", probe.notCulled)
        out.row("frozen (F9)", if (probe.frozen) YES else NO)
        if (probe.boxesNotKept > 0) out.row("boxes not shown", probe.boxesNotKept, warn = true)
    }

    override fun lines(out: DebugLines) {
        for (i in 0 until probe.boxCount) {
            probe.writeMin(i, low)
            probe.writeMax(i, high)
            for (c in 0 until BOX_CORNERS) {
                corners[c].set(
                    if (c and 1 == 0) low.x else high.x,
                    if (c and 2 == 0) low.y else high.y,
                    if (c and 4 == 0) low.z else high.z,
                )
            }
            val color = colorOf(probe.result(i))
            for (e in BOX_EDGES.indices step 2) out.line(corners[BOX_EDGES[e]], corners[BOX_EDGES[e + 1]], color)
        }
        if (probe.hasFrozenView) {
            for (c in 0 until BOX_CORNERS) probe.frozenCorner(c, corners[c])
            for (k in Frustum.EDGES.indices) {
                val edge = Frustum.EDGES[k]
                out.line(corners[edge.first], corners[edge.second], FROZEN_VIEW)
            }
        }
    }

    /** The source's id and colours. */
    companion object {
        /** The id that turns it on: `--debug=culling`, or `?debug=culling` on the web. */
        const val ID: String = "culling"

        /** Drawn meshes. */
        val DRAWN: Color = Color(r = 0.2f, g = 0.9f, b = 0.3f, a = 1f)

        /** Meshes outside the camera's view. */
        val OUTSIDE_VIEW: Color = Color(r = 0.55f, g = 0.55f, b = 0.58f, a = 1f)

        /** Meshes hidden behind an occluder. */
        val OCCLUDED: Color = Color(r = 0.95f, g = 0.25f, b = 0.2f, a = 1f)

        /** Meshes drawn into the shadow maps only. */
        val SHADOW_ONLY: Color = Color(r = 0.65f, g = 0.4f, b = 0.95f, a = 1f)

        /** Copies of instanced meshes, which are never culled. */
        val NOT_CULLED: Color = Color(r = 0.2f, g = 0.85f, b = 0.95f, a = 1f)

        /** The frozen view's frustum. */
        val FROZEN_VIEW: Color = Color(r = 1f, g = 0.9f, b = 0.1f, a = 1f)

        /** The colour [result]'s boxes are drawn in. */
        fun colorOf(result: CullResult): Color =
            when (result) {
                CullResult.Drawn -> DRAWN
                CullResult.OutsideView -> OUTSIDE_VIEW
                CullResult.Occluded -> OCCLUDED
                CullResult.ShadowOnly -> SHADOW_ONLY
                CullResult.NotCulled -> NOT_CULLED
            }

        private const val YES = "yes"
        private const val NO = "no"
        private const val BOX_CORNERS = 8

        /** Corner index pairs (bit 0 is x, bit 1 y, bit 2 z) that differ in one axis: a box's 12 edges. */
        private val BOX_EDGES = intArrayOf(
            0, 1, 2, 3, 4, 5, 6, 7,
            0, 2, 1, 3, 4, 6, 5, 7,
            0, 4, 1, 5, 2, 6, 3, 7,
        )
    }
}
