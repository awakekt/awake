/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.trail

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.planes
import com.awakekt.awake.render.mesh.DynamicMesh
import com.awakekt.awake.render.mesh.GpuDynamicMeshes
import com.awakekt.awake.render.mesh.requireVertexRange
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.pipeline.GpuCapability
import com.awakekt.awake.render.pipeline.GpuCapabilityKind
import com.awakekt.awake.render.testing.NoopRenderer
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dozens of trails, each revealed a few pairs a frame while all its colours fade, allocate nothing a
 * frame once warm: 64 trails of 300 pairs, the case a crowd of swings puts on screen at once.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent. The GPU side is a
 * counting stand-in; the backends' own uploads are covered by the parity pixel tests.
 */
class TrailFrameAllocationTest {
    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    private class CountingMesh(override val vertexCapacity: Int, override val indexCapacity: Int) : DynamicMesh {
        var vertexWrites = 0L
        override val format: VertexFormat = TrailStrip.FORMAT
        override val sizeBytes: Long = 0
        override var drawIndexCount: Int = 0

        override fun writeVertices(vertices: FloatArray, firstVertex: Int, vertexCount: Int) {
            requireVertexRange(vertices, firstVertex, vertexCount)
            vertexWrites += vertexCount
        }

        override fun destroy() = Unit
    }

    private class CountingRenderer : NoopRenderer() {
        val meshes = ArrayList<CountingMesh>()
        private val dynamic = object : GpuDynamicMeshes {
            override fun createDynamicMesh(format: VertexFormat, vertexCapacity: Int, indices: IntArray): DynamicMesh =
                CountingMesh(vertexCapacity, indices.size).also { meshes += it }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? = if (kind == GpuDynamicMeshes) dynamic as T else null
    }

    @Test
    fun sixtyFourTrailsOfThreeHundredPairsUpdateEveryFrameWithoutAllocating() {
        val renderer = CountingRenderer()
        val batch = TrailBatch(renderer)
        val camera = Lens(eye = Vec3f(0f, 2f, 12f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)
        val frustum = Frustum.planes(camera, 16f / 9f)
        val trails = List(TRAILS) {
            TrailRenderer(PAIRS).apply {
                val a = Vec3f(0f, 0f, 0f)
                val b = Vec3f(0f, 0f, 0f)
                for (pair in 0 until PAIRS) {
                    val angle = pair * 0.01f
                    a.set(kotlin.math.cos(angle), 0f, kotlin.math.sin(angle))
                    b.set(a.x * 2f, 0.1f, a.z * 2f)
                    set(pair, a, b, Color.White, Color.White)
                }
            }
        }
        val placements = List(TRAILS) { index ->
            Mat4().setTrs(Vec3f((index % 8) - 4f, 0f, (index / 8) - 4f), Quat.IDENTITY, Vec3f(1f, 1f, 1f))
        }
        val draws = ArrayList<RenderDrawCommand>(TRAILS)
        var frame = 0

        fun step() {
            draws.clear()
            for (i in trails.indices) {
                val trail = trails[i]
                // Reveal a few pairs a frame, starting over once the whole swing has shown.
                trail.visible = 2 + (frame * REVEAL_PER_FRAME + i) % (PAIRS - 1)
                val newest = trail.visible - 1
                for (pair in 0..newest) {
                    val fade = 1f - (newest - pair).toFloat() / PAIRS
                    trail.strip.setColor(pair, fade, fade * 0.6f, fade * 0.2f, 1f)
                }
                batch.collect(trail, placements[i], frustum, draws)
            }
            batch.releaseUnseen()
            frame++
        }

        repeat(WARMUP_FRAMES) { step() }

        // Each window is checked whole and the median window is the gate, as in the other probes: a JIT
        // deoptimisation can materialise a few hundred bytes in one window, a per-frame allocation fills them all.
        val windows = LongArray(MEASURED_WINDOWS)
        repeat(MEASURED_WINDOWS) { window ->
            val before = threadBean.currentThreadAllocatedBytes
            repeat(MEASURED_FRAMES) { step() }
            windows[window] = threadBean.currentThreadAllocatedBytes - before
        }
        windows.sort()
        println("TrailFrameAllocation: ${windows.joinToString()} bytes per $MEASURED_FRAMES frames of $TRAILS trails")

        assertEquals(TRAILS, renderer.meshes.size, "one buffer a trail, kept")
        assertTrue(draws.isNotEmpty(), "the trails are in view")
        assertTrue(renderer.meshes.all { it.vertexWrites > PAIRS * 2L }, "every trail uploads its fading colours")
        assertEquals(0L, windows[MEASURED_WINDOWS / 2], "Steady-state trail updates must allocate 0 bytes over $MEASURED_FRAMES frames")
        batch.close()
    }

    private companion object {
        const val TRAILS = 64
        const val PAIRS = 300
        const val REVEAL_PER_FRAME = 6
        const val WARMUP_FRAMES = 300
        const val MEASURED_WINDOWS = 9
        const val MEASURED_FRAMES = 60
    }
}
