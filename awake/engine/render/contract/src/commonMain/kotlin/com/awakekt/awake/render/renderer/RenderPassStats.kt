/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/** Which part of a frame the draws a backend records count toward. See [RenderStatsCounter.countAs]. */
enum class RenderPassKind {
    /** The scene pass's opaque geometry, its wireframe edges included. */
    SceneOpaque,

    /** The scene pass's blended draws. */
    SceneTransparent,

    /** A shadow depth pass; each one is begun with [RenderStatsCounter.beginShadowPass]. */
    Shadow,

    /** The UI pass. */
    Ui,

    /** Everything else: the sky, debug lines, the camera's depth and mask passes, image passes. */
    Other,
}

/**
 * Draws, instances and triangles one part of a frame recorded, counted as [RenderFrameStats] is.
 *
 * @property drawCalls Draw calls recorded.
 * @property instances Instances across those draws.
 * @property triangles Triangles rasterised, instances included.
 */
data class RenderPassCounts(
    val drawCalls: Int,
    val instances: Int,
    val triangles: Long,
) {
    /** Shared values. */
    companion object {
        /** A part of the frame that drew nothing. */
        val None: RenderPassCounts = RenderPassCounts(0, 0, 0L)
    }
}

/**
 * [RenderFrameStats] split by the part of the frame that recorded each draw. The parts add up to
 * the frame's totals.
 *
 * It tells what culling left and what the shadow passes add: 700 live renderers drawn as 1,500
 * calls read here as, say, 200 opaque draws and 1,300 across four cascades.
 *
 * @property sceneOpaque The scene pass's opaque geometry.
 * @property sceneTransparent The scene pass's blended draws.
 * @property shadowPasses Each shadow depth pass, directional cascades first, then point-shadow faces.
 * @property ui The UI pass.
 * @property other Every other draw: the sky, debug lines, the camera's depth and mask passes, image passes.
 */
data class RenderPassStats(
    val sceneOpaque: RenderPassCounts,
    val sceneTransparent: RenderPassCounts,
    val shadowPasses: List<RenderPassCounts>,
    val ui: RenderPassCounts,
    val other: RenderPassCounts,
) {
    /** Every shadow pass together. */
    val shadow: RenderPassCounts
        get() = RenderPassCounts(
            drawCalls = shadowPasses.sumOf { it.drawCalls },
            instances = shadowPasses.sumOf { it.instances },
            triangles = shadowPasses.sumOf { it.triangles },
        )
}

/** Running counts per [RenderPassKind], plus one slot per shadow pass. Reused frame after frame. */
internal class RenderPassTally {
    private var draws = IntArray(SLOTS)
    private var instances = IntArray(SLOTS)
    private var triangles = LongArray(SLOTS)
    private var shadowPasses = 0
    private var slot = Other

    fun countAs(kind: RenderPassKind) {
        require(kind != RenderPassKind.Shadow) { "A shadow pass is begun with beginShadowPass" }
        slot = kind.ordinal
    }

    fun beginShadowPass() {
        slot = SLOTS + shadowPasses++
        if (slot >= draws.size) {
            draws = draws.copyOf(draws.size * 2)
            instances = instances.copyOf(instances.size * 2)
            triangles = triangles.copyOf(triangles.size * 2)
        }
    }

    fun record(instanceCount: Int, triangleCount: Long) {
        draws[slot] += 1
        instances[slot] += instanceCount
        triangles[slot] += triangleCount
    }

    fun publish(): RenderPassStats {
        val stats = RenderPassStats(
            sceneOpaque = counts(RenderPassKind.SceneOpaque.ordinal),
            sceneTransparent = counts(RenderPassKind.SceneTransparent.ordinal),
            shadowPasses = List(shadowPasses) { counts(SLOTS + it) },
            ui = counts(RenderPassKind.Ui.ordinal),
            other = counts(Other),
        )
        draws.fill(0)
        instances.fill(0)
        triangles.fill(0L)
        shadowPasses = 0
        slot = Other
        return stats
    }

    private fun counts(index: Int): RenderPassCounts =
        if (draws[index] == 0) RenderPassCounts.None else RenderPassCounts(draws[index], instances[index], triangles[index])

    private companion object {
        val SLOTS = RenderPassKind.entries.size
        val Other = RenderPassKind.Other.ordinal
    }
}
