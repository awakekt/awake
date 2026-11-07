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
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.planes
import com.awakekt.awake.render.mesh.DynamicMesh
import com.awakekt.awake.render.mesh.GpuDynamicMeshes
import com.awakekt.awake.render.mesh.requireVertexRange
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.pipeline.GpuCapability
import com.awakekt.awake.render.pipeline.GpuCapabilityKind
import com.awakekt.awake.render.testing.NoopRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** [TrailBatch]: one buffer a trail, the changed range uploaded alone, nothing for [TrailRenderer.visible]. */
class TrailBatchTest {
    /** A write: the first vertex and how many. */
    private data class Write(val first: Int, val count: Int)

    private class FakeMesh(override val vertexCapacity: Int, override val indexCapacity: Int) : DynamicMesh {
        val writes = ArrayList<Write>()
        var destroyed = false
        override val format: VertexFormat = TrailStrip.FORMAT
        override val sizeBytes: Long = 0
        override var drawIndexCount: Int = 0

        override fun writeVertices(vertices: FloatArray, firstVertex: Int, vertexCount: Int) {
            requireVertexRange(vertices, firstVertex, vertexCount)
            writes += Write(firstVertex, vertexCount)
        }

        override fun destroy() {
            destroyed = true
        }
    }

    private class DynamicRenderer(private val supported: Boolean = true) : NoopRenderer() {
        val meshes = ArrayList<FakeMesh>()
        private val dynamic = object : GpuDynamicMeshes {
            override fun createDynamicMesh(format: VertexFormat, vertexCapacity: Int, indices: IntArray): DynamicMesh =
                FakeMesh(vertexCapacity, indices.size).also { meshes += it }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? =
            if (supported && kind == GpuDynamicMeshes) dynamic as T else null
    }

    private val camera = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)
    private val frustum = Frustum.planes(camera, 1f)

    private fun trail(capacity: Int = 8) = TrailRenderer(capacity).apply {
        for (i in 0 until capacity) set(i, Vec3f(i * 0.1f, 0f, 0f), Vec3f(i * 0.1f, 0.2f, 0f), Color.White, Color.White)
    }

    @Test
    fun aTrailGetsOneBufferForItsCapacityAndIsDrawnTransparent() {
        val renderer = DynamicRenderer()
        val batch = TrailBatch(renderer)
        val trail = trail().apply { visible = 4 }
        val draws = ArrayList<RenderDrawCommand>()
        repeat(3) {
            draws.clear()
            batch.collect(trail, Mat4(), frustum, draws)
            batch.releaseUnseen()
        }

        val mesh = renderer.meshes.single()
        assertEquals(16, mesh.vertexCapacity, "two vertices a pair")
        assertEquals(42, mesh.indexCapacity, "two triangles between each pair and the next")
        assertEquals(listOf(Write(0, 16)), mesh.writes, "filled once, whole")
        assertEquals(18, mesh.drawIndexCount, "three segments between four visible pairs")
        val draw = draws.single()
        assertSame(mesh, draw.mesh)
        assertTrue(draw.transparent)
        assertTrue(draw.additive)
        assertFalse(draw.castsShadows)
    }

    @Test
    fun aFrameUploadsOnlyThePairsThatChangedAndVisibleAloneUploadsNothing() {
        val renderer = DynamicRenderer()
        val batch = TrailBatch(renderer)
        val trail = trail().apply { visible = 2 }
        val draws = ArrayList<RenderDrawCommand>()
        batch.collect(trail, Mat4(), frustum, draws)
        val mesh = renderer.meshes.single()

        trail.visible = 6
        batch.collect(trail, Mat4(), frustum, draws)
        assertEquals(1, mesh.writes.size, "revealing pairs uploads nothing")
        assertEquals(30, mesh.drawIndexCount)

        trail.strip.setColor(5, 0.5f, 0.5f, 0.5f, 1f)
        trail.strip.setColor(3, 0.5f, 0.5f, 0.5f, 1f)
        batch.collect(trail, Mat4(), frustum, draws)
        assertEquals(Write(6, 6), mesh.writes.last(), "pairs 3 to 5, two vertices each")
        assertEquals(2L, batch.uploadCount)
    }

    @Test
    fun aTrailOutOfViewOrWithOnePairIsNotDrawnAndBlendAndTintReachTheDraw() {
        val batch = TrailBatch(DynamicRenderer())
        val draws = ArrayList<RenderDrawCommand>()
        val trail = trail().apply { visible = 1 }
        assertFalse(batch.collect(trail, Mat4(), frustum, draws))

        trail.visible = 8
        val behind = Mat4().setTrs(Vec3f(0f, 0f, 50f), com.awakekt.awake.core.math.Quat.IDENTITY, Vec3f(1f, 1f, 1f))
        assertFalse(batch.collect(trail, behind, frustum, draws), "behind the camera")
        assertTrue(batch.collect(trail, behind, emptyList(), draws), "no planes cull nothing")

        trail.blend = TrailBlend.Alpha
        trail.tint = Color(0.25f, 0.5f, 0.75f, 1f)
        draws.clear()
        batch.collect(trail, Mat4(), frustum, draws)
        assertFalse(draws.single().additive)
        assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f), draws.single().extraUniformFloats.toList())
    }

    @Test
    fun aTrailNoFrameDrawsIsFreedAndARendererWithoutTheCapabilityDrawsNothing() {
        val renderer = DynamicRenderer()
        val batch = TrailBatch(renderer)
        val draws = ArrayList<RenderDrawCommand>()
        batch.collect(trail().apply { visible = 3 }, Mat4(), frustum, draws)
        batch.releaseUnseen()
        batch.releaseUnseen()
        assertTrue(renderer.meshes.single().destroyed)

        val without = DynamicRenderer(supported = false)
        val headless = TrailBatch(without)
        assertFalse(headless.canDraw)
        assertFalse(headless.collect(trail().apply { visible = 3 }, Mat4(), frustum, draws))
        assertTrue(without.meshes.isEmpty())
        batch.close()
        headless.close()
    }
}
