/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.EdgeFade
import com.awakekt.awake.core.graphics2d.TextureRegion
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Benchmarks [DrawRunCoalescer] dynamic `TextureRun` pooling and [EdgeFadeGrid] tessellation
 * under `-XX:-DoEscapeAnalysis` (matching Kotlin/Wasm allocation semantics).
 */
class TextureRunPoolAllocationTest {
    @Test
    fun edgeFadeGridWriteAllocatesNothing() {
        val tex = Any()
        val fade = EdgeFade(left = 0.25f, top = 0f, right = 0.25f, bottom = 0.65f)
        val primitive = DrawCommand.Texture(0f, 0f, 176f, 112f, tex, edgeFade = fade)
        val grid = EdgeFadeGrid.forFade(fade)
        val vertices = FloatArray(grid.vertexCount * VertexFormats2D.TEXTURE_FLOATS_PER_VERTEX)
        val indices = IntArray(grid.indexCount)

        repeat(WARMUP) { writeOnce(fade, primitive, vertices, indices) }
        val tFade = System.nanoTime()
        val fadeAlloc = readAllocation(FRAMES) { repeat(FRAMES) { writeOnce(fade, primitive, vertices, indices) } }
        val fadeUs = (System.nanoTime() - tFade) / (FRAMES * READ_ALLOC_WINDOWS) / NANOS_PER_MICRO
        println("EDGE-FADE-GRID-BENCH (${grid.vertexCount} verts, -XX:-DoEscapeAnalysis): ${fadeAlloc.perUnit} B/f ($fadeUs us/f)")
        assertAllocatesNothing(fadeAlloc, "EdgeFadeGrid lookup and vertex/index tessellation")
    }

    @Test
    fun pooledDynamicTextureRunsCutPerFrameGarbage() {
        val tex = Any()
        val fade = EdgeFade(left = 0.25f, top = 0f, right = 0.25f, bottom = 0.65f)
        // Dynamic batches cycling over a ring larger than RetainedDrawRunCache's LRU capacity so every
        // frame misses the retained cache (matching scrolling minimaps / moving HUD icons).
        val batches = Array(BATCH_RING_SIZE) { i ->
            buildBatch(tex, fade, offset = i * 0.25f)
        }
        val retainedPooled = RetainedDrawRunCache()
        val pool = DynamicRunArrayPool()
        val retainedUnpooled = RetainedDrawRunCache()

        for (i in 0 until WARMUP) {
            val batch = batches[i % BATCH_RING_SIZE]
            DrawRunCoalescer.coalesce(batch, retained = retainedPooled, pool = pool)
            DrawRunCoalescer.coalesce(batch, retained = retainedUnpooled, pool = null)
        }
        val resizesBefore = pool.resizeCount

        var cursor = WARMUP
        val tPooled = System.nanoTime()
        val pooledAlloc = readAllocation(FRAMES) {
            repeat(FRAMES) {
                DrawRunCoalescer.coalesce(batches[cursor++ % BATCH_RING_SIZE], retained = retainedPooled, pool = pool)
            }
        }
        val pooledUs = (System.nanoTime() - tPooled) / (FRAMES * READ_ALLOC_WINDOWS) / NANOS_PER_MICRO

        val tUnpooled = System.nanoTime()
        val unpooledAlloc = readAllocation(FRAMES) {
            repeat(FRAMES) {
                DrawRunCoalescer.coalesce(batches[cursor++ % BATCH_RING_SIZE], retained = retainedUnpooled, pool = null)
            }
        }
        val unpooledUs = (System.nanoTime() - tUnpooled) / (FRAMES * READ_ALLOC_WINDOWS) / NANOS_PER_MICRO

        println(
            "TEXTURE-RUN-POOL-BENCH ($QUAD_COUNT quads + 1 EdgeFade, -XX:-DoEscapeAnalysis): " +
                "pooled=${pooledAlloc.perUnit} B/f ($pooledUs us/f, resizes=${pool.resizeCount - resizesBefore}) vs " +
                "unpooled=${unpooledAlloc.perUnit} B/f ($unpooledUs us/f)",
        )
        assertEquals(resizesBefore, pool.resizeCount, "steady pooled dynamic texture batch must never resize pool arrays")
        assertTrue(
            pooledAlloc.perUnit < unpooledAlloc.perUnit / 4,
            "pooled TextureRun (${pooledAlloc.perUnit} B/f) should cut at least 75% vs unpooled (${unpooledAlloc.perUnit} B/f)",
        )
    }

    private fun writeOnce(fade: EdgeFade, primitive: DrawCommand.Texture, vertices: FloatArray, indices: IntArray) {
        EdgeFadeGrid.forFade(fade).writeTo(
            primitive = primitive,
            region = TextureRegion.Whole,
            repeatX = false,
            repeatY = false,
            baseColor = Color.White,
            transform = null,
            vertices = vertices,
            indices = indices,
            vertexStart = 0,
            indexStart = 0,
            vertexOffset = 0,
            spanU = 0f,
            spanV = 0f,
        )
    }

    private fun buildBatch(tex: Any, fade: EdgeFade, offset: Float): List<DrawCommand> = buildList(QUAD_COUNT + 1) {
        add(DrawCommand.Texture(offset, 0f, 176f, 112f, tex, edgeFade = fade))
        for (i in 0 until QUAD_COUNT) {
            add(DrawCommand.Texture(offset + i * 24f, 120f, 20f, 20f, tex))
        }
    }

    private companion object {
        const val QUAD_COUNT = 32
        const val BATCH_RING_SIZE = 512
        const val WARMUP = 120
        const val FRAMES = 100
        const val READ_ALLOC_WINDOWS = 5
        const val NANOS_PER_MICRO = 1_000.0
    }
}
