/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #1545's gate: a still frame of a vector-drawn UI re-stages nothing and uploads nothing.
 *
 * [VectorHudFixture] is rebuilt every frame, as composition rebuilds it, and staged through the
 * renderer's retained cache and pool. [SlotUploader] models both backends' `DynamicMesh.update`: a
 * slot uploads when the run is forced or its arrays are not the instances the slot last received,
 * and an upload writes the vertex and index arrays whole.
 *
 * On main at 0.46.0 the fixture's 282 runs upload 2,397,148 bytes on the first frame, then **26 runs,
 * 3,295 triangles and 251,804 bytes on every still frame after it**: the spans past the retained
 * cache's fixed 256 entries evict each other and are re-staged and re-uploaded forever. Since the
 * cache keeps every span of the frame being drawn, a still frame costs 0.
 */
class StillVectorHudUploadGateTest {

    private class SlotUploader : DrawMeshUploader<Int> {
        private val last = HashMap<Int, FloatArray>()
        var bytes = 0L
        var runs = 0

        private fun write(slot: Int, vertices: FloatArray, indices: IntArray, force: Boolean): Int {
            if (indices.isNotEmpty() && (force || last[slot] !== vertices)) {
                bytes += (vertices.size + indices.size).toLong() * Float.SIZE_BYTES
                runs += 1
            }
            last[slot] = vertices
            return slot
        }

        override fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = write(runIndex, vertices, indices, false)
        override fun roundedQuadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) =
            write(ROUNDED or runIndex, vertices, indices, false)
        override fun glyphMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = write(GLYPH or runIndex, vertices, indices, false)
        override fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray, forceUpload: Boolean) =
            write(runIndex, vertices, indices, forceUpload)
        override fun roundedQuadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray, forceUpload: Boolean) =
            write(ROUNDED or runIndex, vertices, indices, forceUpload)
        override fun glyphMesh(runIndex: Int, vertices: FloatArray, indices: IntArray, forceUpload: Boolean) =
            write(GLYPH or runIndex, vertices, indices, forceUpload)
    }

    /** One frame's cost: runs staged afresh, triangles in them, runs and bytes written to the GPU. */
    private data class FrameCost(val restagedRuns: Int, val restagedTriangles: Long, val uploadedRuns: Int, val uploadedBytes: Long)

    private fun stillFrames(frames: Int): List<FrameCost> {
        val cache = RetainedDrawRunCache()
        val pool = DynamicRunArrayPool()
        val uploader = SlotUploader()
        var previous = emptySet<FloatArray>()
        return List(frames) {
            val staged = DrawRunCoalescer.coalesce(VectorHudFixture.frame(), retained = cache, pool = pool)
            val meshes = staged.mapNotNull { it.arrays() }
            val fresh = meshes.filter { (run, vertices) -> run.requiresUpload || vertices !in previous }
            previous = meshes.mapTo(HashSet()) { it.second }
            val bytesBefore = uploader.bytes
            val runsBefore = uploader.runs
            uploadDrawRuns(staged, uploader, VectorHudFixture.font)
            val triangles = fresh.sumOf { (run, _) -> run.arrays()!!.third.size / 3L }
            FrameCost(fresh.size, triangles, uploader.runs - runsBefore, uploader.bytes - bytesBefore)
        }
    }

    private fun StagedDrawRun.arrays(): Triple<StagedDrawRun, FloatArray, IntArray>? = when (this) {
        is StagedDrawRun.QuadRun -> Triple(this, vertices, indices)
        is StagedDrawRun.RoundedQuadRun -> Triple(this, vertices, indices)
        is StagedDrawRun.GlyphRun -> Triple(this, vertices, indices)
        else -> null
    }

    @Test
    fun aStillVectorHudReStagesAndUploadsNothingAfterTheFirstFrame() {
        val costs = stillFrames(FRAMES)
        costs.forEachIndexed { frame, cost -> println("PERF stillVectorHud frame=$frame $cost") }
        assertTrue(costs.first().uploadedBytes > 0, "the first frame uploads the HUD")
        costs.drop(1).forEachIndexed { index, cost ->
            assertEquals(FrameCost(0, 0, 0, 0), cost, "still frame ${index + 1} must reuse every staged run")
        }
    }

    private companion object {
        const val FRAMES = 8
        const val ROUNDED = 1 shl 24
        const val GLYPH = 1 shl 25
    }
}
