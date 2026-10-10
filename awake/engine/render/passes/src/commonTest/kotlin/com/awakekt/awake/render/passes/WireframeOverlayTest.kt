/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.math.transformPosition
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.WIREFRAME_EDGE_CODE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WireframeOverlayTest {

    /** An edge lands on its point's pixel, nearer than the point, and behind a face 1% nearer still. */
    @Test
    fun anEdgeKeepsItsPixelAndComesJustNearer() {
        listOf(Lens.Projection.Perspective, Lens.Projection.Orthographic).forEach { projection ->
            ClipSpace.entries.forEach { clipSpace ->
                val lens = lens(projection)
                val viewProjection = lens.viewProjectionMatrix(ASPECT, clipSpace)
                val edges = edgeViewProjection(viewProjection, lens.eye)
                POINTS.forEach { point ->
                    val label = "$projection, $clipSpace, $point"
                    val drawn = viewProjection.ndc(point)
                    val edge = edges.ndc(point)
                    assertEquals(drawn.x, edge.x, PIXEL_TOLERANCE, "same pixel across: $label")
                    assertEquals(drawn.y, edge.y, PIXEL_TOLERANCE, "same pixel down: $label")
                    assertTrue(edge.z < drawn.z, "nearer than its face: $label")
                    val nearerFace = lens.eye + (point - lens.eye) * NEARER_FACE
                    assertTrue(edge.z > viewProjection.ndc(nearerFace).z, "behind a face 1% nearer: $label")
                }
            }
        }
    }

    @Test
    fun wireframeOnPreparesEachOpaqueDrawsEdgesAfterTheVisibleDraws() {
        val prepared = ArrayList<Pair<Int, GpuDrawPreparationContext>>()
        val draws = listOf(
            RenderDrawCommand(mesh, material),
            RenderDrawCommand(mesh, material, transparent = true),
            RenderDrawCommand(mesh, material, shadowsOnly = true),
            RenderDrawCommand(mesh, material),
        )
        val input = compile(draws, wireframe = true, prepared)

        val edgeCalls = prepared.filter { (_, context) -> context.edges }
        assertEquals(listOf(4, 7), edgeCalls.map { it.first }, "the opaque draws' edges, numbered after all four draws")
        edgeCalls.forEach { (_, context) ->
            assertEquals(WIREFRAME_EDGE_CODE, context.environment.debugView.code)
            assertFalse(context.viewProjection.data.contentEquals(prepared.first().second.viewProjection.data))
        }
        assertEquals(2, input.resolvedEdgeDraws.size)
        assertEquals(2, input.resolvedOpaqueDraws.size)
    }

    @Test
    fun wireframeOffPreparesNoEdges() {
        val prepared = ArrayList<Pair<Int, GpuDrawPreparationContext>>()
        val input = compile(listOf(RenderDrawCommand(mesh, material)), wireframe = false, prepared)

        assertEquals(listOf(0), prepared.map { it.first })
        assertTrue(input.resolvedEdgeDraws.isEmpty())
    }

    @Test
    fun onlySingleOpaqueDrawsWhoseShaderShowsDebugViewsDrawEdges() {
        val lit = MaterialUniformLayouts.LitShadow.total
        assertTrue(RenderDrawCommand(meshOf(VertexFormat.PositionNormalColor), material).drawsEdges(lit))
        assertTrue(RenderDrawCommand(meshOf(VertexFormat.PositionNormalColorUv), material).drawsEdges(MaterialUniformLayouts.PbrTextured.total))
        assertTrue(RenderDrawCommand(meshOf(VertexFormat.PositionNormalColorUvSkin), material).drawsEdges(0))
        assertFalse(RenderDrawCommand(meshOf(VertexFormat.PositionNormalColor), material, instanceModels = listOf(Mat4())).drawsEdges(lit))
        assertFalse(RenderDrawCommand(meshOf(VertexFormat.PositionNormalColor), material, transparent = true).drawsEdges(lit))
        assertFalse(RenderDrawCommand(meshOf(VertexFormat.PositionNormalColor), material, shadowsOnly = true).drawsEdges(lit))
        assertFalse(RenderDrawCommand(meshOf(VertexFormat.PositionColorUv), material).drawsEdges(lit), "an unlit shader")
    }

    private fun compile(
        draws: List<RenderDrawCommand>,
        wireframe: Boolean,
        prepared: MutableList<Pair<Int, GpuDrawPreparationContext>>,
    ) = ScenePassCompiler.compile(
        lens = lens(Lens.Projection.Perspective),
        drawCalls = draws,
        environment = EnvironmentUniforms(wireframe = wireframe),
        clipSpace = ClipSpace.Vulkan,
        aspect = ASPECT,
        drawPreparer = GpuDrawPreparer { request, sourceIndex, context ->
            prepared += sourceIndex to context
            GpuResolvedDraw(
                pipeline = object : PipelineHandle {},
                materialBinding = object : MaterialBinding {},
                vertexBuffer = object : BufferHandle {},
                indexBuffer = null,
                elementCount = 3,
                transparent = request.transparent,
            )
        },
    )

    private fun lens(projection: Lens.Projection) =
        Lens(eye = Vec3f(2f, 3f, 6f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 200f).also {
            it.projection = projection
            it.orthoHalfHeight = 8f
        }

    private fun Mat4.ndc(point: Vec3f): Vec3f {
        val clip = transformPosition(Vec4(point.x, point.y, point.z, 1f))
        return Vec3f(clip.x / clip.w, clip.y / clip.w, clip.z / clip.w)
    }

    private fun meshOf(vertexFormat: VertexFormat) = object : Mesh {
        override val format = vertexFormat
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private val mesh = meshOf(VertexFormat.PositionNormalColor)

    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val ASPECT = 16f / 9f
        const val NEARER_FACE = 0.99f

        // A tenth of a pixel across 1920 pixels of NDC.
        const val PIXEL_TOLERANCE = 0.1f * 2f / 1920f

        // Near and far, on the axis and off it.
        val POINTS = listOf(
            Vec3f.ZERO,
            Vec3f(1.5f, -0.5f, 0.5f),
            Vec3f(-4f, 1f, -3f),
            Vec3f(-30f, 0f, -120f),
            Vec3f(1.9f, 2.9f, 5.5f),
        )
    }
}
