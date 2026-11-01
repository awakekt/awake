/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the #1221 identity-skip coupling against the staging-array pool (#1224).
 *
 * Both backends' `DynamicMesh.update` skip the GPU upload when the staged arrays are the same
 * instances the mesh slot last received. [IdentitySkipUploader] models exactly that rule, so these
 * tests prove the two halves the pool must keep true:
 *  - a run whose contents change every frame is handed a different array instance each frame and
 *    therefore re-uploads (never wrongly skipped into a stale buffer), and
 *  - an unchanged, retained run is handed the same immutable array each frame and therefore skips.
 */
class UiRunStagingIdentityTest {

    /** Models a backend `DynamicMesh` pool: one slot per `(kind, runIndex)`, skipping the upload
     * when handed the same array instance as last time, counting uploads and skips. */
    private class IdentitySkipUploader : DrawMeshUploader<Int> {
        private val lastVertices = HashMap<String, FloatArray>()
        var uploads = 0
            private set
        var skips = 0
            private set

        private fun record(key: String, vertices: FloatArray): Int {
            if (lastVertices[key] === vertices) {
                skips += 1
            } else {
                uploads += 1
                lastVertices[key] = vertices
            }
            return key.hashCode()
        }

        override fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): Int =
            record("quad:$runIndex", vertices)

        override fun roundedQuadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): Int =
            record("rounded:$runIndex", vertices)

        override fun glyphMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): Int =
            record("glyph:$runIndex", vertices)
    }

    private fun quad(x: Float) = DrawCommand.Quad(x, 0f, 10f, 10f, Color.White)

    /**
     * A run whose contents change every frame uploads on every frame. If the pool ever handed the
     * same instance twice running, the modelled skip would fire and this would read fewer uploads.
     *
     * Negative control: routing the dynamic run through a single-buffer pool (same instance each
     * frame) measured `uploads == 1` and `skips == 4` here -- the stale-buffer misfire.
     */
    @Test
    fun aRunWhoseContentsChangeEveryFrameReUploadsThroughThePool() {
        val cache = RetainedDrawRunCache()
        val pool = DynamicRunArrayPool()
        val uploader = IdentitySkipUploader()
        repeat(FRAMES) { frame ->
            val staged = DrawRunCoalescer.coalesce(listOf(quad(frame.toFloat())), retained = cache, pool = pool)
            uploadDrawRuns(staged, uploader)
        }
        assertEquals(FRAMES, uploader.uploads, "every changed frame must upload")
        assertEquals(0, uploader.skips, "a changing run must never be skipped")
    }

    /**
     * An unchanged run uploads once, then skips. The retained cache hands back the same immutable
     * array each frame and the pool is never consulted for it, so the modelled skip fires.
     *
     * Negative control: disabling the retained cache (passing `retained = null`) measured
     * `uploads == FRAMES` and `skips == 0` -- every unchanged frame re-uploaded.
     */
    @Test
    fun anUnchangedRetainedRunSkipsItsUploadAfterTheFirstFrame() {
        val cache = RetainedDrawRunCache()
        val pool = DynamicRunArrayPool()
        val uploader = IdentitySkipUploader()
        repeat(FRAMES) {
            val staged = DrawRunCoalescer.coalesce(listOf(quad(0f)), retained = cache, pool = pool)
            uploadDrawRuns(staged, uploader)
        }
        assertEquals(1, uploader.uploads, "an unchanged run uploads only once")
        assertEquals(FRAMES - 1, uploader.skips, "every later unchanged frame skips")
    }

    /**
     * A mixed scene -- one static quad and one quad that moves every frame -- keeps both rules at
     * once: the static slot skips after its first upload while the moving slot re-uploads each
     * frame.
     */
    @Test
    fun aStaticAndADynamicRunInOneSceneEachBehaveCorrectly() {
        val cache = RetainedDrawRunCache()
        val pool = DynamicRunArrayPool()
        val uploader = IdentitySkipUploader()
        repeat(FRAMES) { frame ->
            // Two separate spans: a clip between them keeps them from coalescing into one run.
            val staged = DrawRunCoalescer.coalesce(
                listOf(
                    quad(0f),
                    DrawCommand.ClipPush(com.awakekt.awake.core.math2d.Rectangle(0f, 0f, 1000f, 1000f)),
                    quad(frame.toFloat() + 1f),
                ),
                retained = cache,
                pool = pool,
            )
            uploadDrawRuns(staged, uploader)
        }
        // quad:0 is static (1 upload), quad:1 moves (FRAMES uploads). Clips carry no mesh.
        assertEquals(FRAMES + 1, uploader.uploads)
    }

    private companion object {
        const val FRAMES = 5
    }
}
