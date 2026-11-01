/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.ColoredVertex
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawPoint
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves #1224's staging-array reuse: a steady full-HUD-like scene whose dynamic runs change every
 * frame allocates far less per frame once a [DynamicRunArrayPool] feeds the coalescer, because the
 * per-run `FloatArray`/`IntArray` -- the 42% the JFR attributed to `stagePlacedMeshesToRun` and the
 * quad builders -- are reused instead of allocated each frame.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent (see
 * `awake-ui-performance`). This drives only the backend-neutral coalescer + upload staging, which
 * is where the arrays are built; the GPU upload itself is a backend concern measured elsewhere.
 */
class UiRunStagingAllocationProbeTest {

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private val white = Color(1f, 1f, 1f, 1f)

    // One shared already-tessellated mesh, like a character rig's geometry; built once.
    private val character = ColoredTriangleMesh(
        (0 until 600).map { ColoredVertex(DrawPoint((it % 20).toFloat(), (it / 20).toFloat()), white) },
        IntArray(600) { it },
    )

    /** A HUD-shaped frame: static chrome plus [moving] runs whose geometry changes every frame --
     * 22 skinned-character-like meshes, moving quads, rounded panels and shadows. Clips keep the
     * spans from coalescing so each stages as its own run. */
    private fun hudFrame(moving: Float): List<DrawCommand> = buildList {
        fun clip() = add(DrawCommand.ClipPush(com.awakekt.awake.core.math2d.Rectangle(0f, 0f, 4000f, 4000f)))
        // Static chrome: unchanged every frame, served by the retained cache after frame 1.
        repeat(24) { i -> add(DrawCommand.Quad((i % 8) * 40f, (i / 8) * 24f, 36f, 20f, white)) }
        clip()
        // Moving quads (HUD bars, cursors).
        repeat(16) { i -> add(DrawCommand.Quad((i % 8) * 40f + moving, 200f, 30f, 8f, white)) }
        clip()
        // Rounded panels and drop shadows that drift.
        repeat(8) { i -> add(DrawCommand.RoundedQuad(i * 60f + moving, 300f, 50f, 40f, white, radius = 6f)) }
        clip()
        repeat(8) { i ->
            add(
                DrawCommand.ShadowQuad(
                    x = i * 60f + moving, y = 420f, w = 50f, h = 40f, radius = 6f,
                    offsetX = 0f, offsetY = 0f, blurRadius = 8f, spread = 0f, color = white,
                ),
            )
        }
        clip()
        // 22 "skinned characters": already-tessellated meshes re-placed every frame -- the 42%.
        repeat(22) { i ->
            add(DrawCommand.Mesh(character, offsetX = (i % 6) * 48f + moving, offsetY = 360f + (i / 6) * 48f))
        }
    }

    private class Noop : DrawMeshUploader<Int> {
        override fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = runIndex
        override fun roundedQuadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = runIndex
        override fun glyphMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = runIndex
    }

    // Frames are pre-built once, so the measured loop allocates only in staging, never in the
    // immutable scene DrawCommands. Cycling distinct frames keeps the dynamic runs changing (a
    // retained-cache miss every frame), which is exactly what must stage without allocating.
    private val frames: List<List<DrawCommand>> = List(FRAME_RING) { hudFrame(it.toFloat()) }

    /** Bytes allocated per frame staging the pre-built frames, taking the quietest window so a
     * stray GC or JIT compile in one window does not inflate the figure. */
    private fun bytesPerFrame(pool: DynamicRunArrayPool?): Long {
        val cache = RetainedDrawRunCache()
        val uploader = Noop()
        fun frame(f: Int) {
            val staged = DrawRunCoalescer.coalesce(frames[f % FRAME_RING], retained = cache, pool = pool)
            uploadDrawRuns(staged, uploader)
        }
        repeat(WARMUP) { frame(it) }
        var quietest = Long.MAX_VALUE
        var f = WARMUP
        repeat(WINDOWS) {
            val start = allocated()
            repeat(WINDOW) { frame(f++) }
            quietest = minOf(quietest, (allocated() - start) / WINDOW)
        }
        return quietest
    }

    /**
     * Measured 2026-11-01 on this CI host: **before=700,216 bytes/frame** with no pool,
     * **after=2,080 bytes/frame** with the pool -- a 99.7% drop. The before figure is the per-run
     * `FloatArray`/`IntArray` the coalescer rebuilt for every changing run (the JFR's 42%
     * `stagePlacedMeshesToRun` plus the quad/rounded/shadow builders); the 2 KB that remains is the
     * small `StagedDrawRun`/`DrawRun` wrapper objects and lists, not geometry arrays.
     *
     * The bound is set well inside that gap: a regression that stopped reusing the arrays would
     * blow straight past [MAX_POOLED_BYTES_PER_FRAME], and [MIN_REDUCTION_FACTOR] fails if the drop
     * is not at least 50x. Negative control: running the measured loop with `pool = null` is the
     * `before` figure itself -- two orders of magnitude over the bound.
     */
    @Test
    fun poolingTheStagingArraysCutsPerFrameAllocationToNearlyZero() {
        val before = bytesPerFrame(pool = null)
        val after = bytesPerFrame(pool = DynamicRunArrayPool())
        println("PERF uiRunStaging bytes/frame before=$before after=$after reduction=${100 - after * 100 / before}%")
        assertTrue(
            after < MAX_POOLED_BYTES_PER_FRAME,
            "pooled staging should allocate almost nothing per frame, but measured $after bytes " +
                "(ceiling $MAX_POOLED_BYTES_PER_FRAME); the per-run arrays are not being reused.",
        )
        assertTrue(
            after * MIN_REDUCTION_FACTOR < before,
            "pooling must cut staging allocation at least ${MIN_REDUCTION_FACTOR}x (before=$before after=$after).",
        )
    }

    private companion object {
        const val WARMUP = 200
        const val WINDOW = 50
        const val WINDOWS = 8

        // Larger than RetainedDrawRunCache's capacity so a frame's content has evicted before the
        // ring wraps to it again: the no-pool baseline keeps missing and re-staging, which is the
        // churning-content case the pool exists for, rather than the cache quietly absorbing it.
        const val FRAME_RING = 400

        // Comfortably above the ~2 KB of wrappers measured, far below the ~700 KB of arrays removed.
        const val MAX_POOLED_BYTES_PER_FRAME = 8_192L
        const val MIN_REDUCTION_FACTOR = 50L
    }
}
