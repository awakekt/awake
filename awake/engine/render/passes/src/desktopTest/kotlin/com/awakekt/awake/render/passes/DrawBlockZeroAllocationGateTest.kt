/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.math.putVec4
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.DrawBlockWriters
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.ParticleDrawLayout
import com.awakekt.awake.render.passes.uniforms.drawBlockFloats
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.renderer.SkinnedDrawLayout
import com.awakekt.awake.render.renderer.SkinnedMaterialLayout
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that packing moving textured (full, legacy 12-float, and plain default), particle, and
 * skinned draw blocks through [DrawBlockWriters] allocates zero bytes per draw, and that
 * [FrameUniformInputs], [DrawUniformInputs.record], [InstanceTints], and [putVec4] allocate zero
 * bytes in steady state (#1742, #1621).
 */
class DrawBlockZeroAllocationGateTest {
    private var sink: Any? = null
    private var intSink: Int = 0

    @Test
    fun texturedDrawBlocksAllocateNothingAcrossAllPayloadShapes() {
        val writers = DrawBlockWriters()
        val mesh = stubMesh(VertexFormat.PositionNormalColorUv)
        val fullDraw = RenderDrawCommand(
            mesh = mesh,
            material = stubMaterial,
            model = Mat4().translate(1f, 2f, 3f),
            extraUniformFloats = FloatArray(MaterialUniformLayouts.PbrTexturedMaterial.total) { 0.1f * it },
        )
        val legacyDraw = RenderDrawCommand(
            mesh = mesh,
            material = stubMaterial,
            model = Mat4().translate(4f, 5f, 6f),
            extraUniformFloats = FloatArray(LEGACY_FACTOR_FLOATS) { 0.2f * it },
        )
        val plainDraw = RenderDrawCommand(
            mesh = mesh,
            material = stubMaterial,
            model = Mat4().translate(7f, 8f, 9f),
            extraUniformFloats = FloatArray(0),
        )
        val draws = arrayOf(fullDraw, legacyDraw, plainDraw)
        val floats = MaterialUniformLayouts.PbrTexturedDraw.total

        // Sanity: writer-packed output matches fresh packing bit-for-bit across all three payload shapes.
        for (draw in draws) {
            val fresh = draw.drawBlockFloats(floats, alphaToCoverageEnabled = true, instancedKind = null)
            val kept = draw.drawBlockFloats(floats, alphaToCoverageEnabled = true, instancedKind = null, writers = writers)
            assertEquals(fresh.toList(), kept.toList())
        }

        val reading = readAllocation(unitsPerWindow = ITERATIONS) {
            for (i in 0 until ITERATIONS) {
                sink = draws[i % draws.size].drawBlockFloats(floats, alphaToCoverageEnabled = true, instancedKind = null, writers = writers)
            }
        }
        assertAllocatesNothing(reading, "Textured drawBlockFloats (full, legacy, plain) with DrawBlockWriters")
    }

    @Test
    fun particleAndSkinnedDrawBlocksAllocateNothingWithWriters() {
        val writers = DrawBlockWriters()
        val particleDraw = RenderDrawCommand(
            mesh = stubMesh(VertexFormat.PositionNormalColorUv),
            material = stubMaterial,
            model = Mat4(),
            extraUniformFloats = FloatArray(ParticleDrawLayout.total) { 0.5f + it },
        )
        val skinnedFullDraw = RenderDrawCommand(
            mesh = stubMesh(VertexFormat.PositionNormalColorSkin),
            material = stubMaterial,
            model = Mat4().translate(2f, 0f, -2f),
            extraUniformFloats = FloatArray(SkinnedMaterialLayout.total) { 0.01f * it },
        )
        val skinnedShortDraw = RenderDrawCommand(
            mesh = stubMesh(VertexFormat.PositionNormalColorSkin),
            material = stubMaterial,
            model = Mat4().translate(-2f, 0f, 2f),
            extraUniformFloats = FloatArray(32) { 0.02f * it },
        )

        val reading = readAllocation(unitsPerWindow = ITERATIONS) {
            for (i in 0 until ITERATIONS) {
                sink = particleDraw.drawBlockFloats(ParticleDrawLayout.total, true, InstancedDrawKind.Particle, writers)
                sink = skinnedFullDraw.drawBlockFloats(SkinnedDrawLayout.total, true, null, writers)
                sink = skinnedShortDraw.drawBlockFloats(SkinnedDrawLayout.total, true, null, writers)
            }
        }
        assertAllocatesNothing(reading, "Particle and skinned drawBlockFloats with DrawBlockWriters")
    }

    @Test
    fun uniformInputsTintsAndPutVec4AllocateNothingInSteadyState() {
        val frameInputs = FrameUniformInputs()
        val viewProjection = Mat4()
        val eye = Vec3f(1f, 2f, 3f)
        val forward = Vec3f(0f, 0f, -1f)
        val lightPayload = FloatArray(MaterialUniformLayouts.SceneLight.total) { 0.25f }
        val cascades = GpuShadowCascadeData(listOf(Mat4(), Mat4()), floatArrayOf(10f, 40f))
        val drawInputs = DrawUniformInputs()
        val mesh = stubMesh(VertexFormat.PositionNormalColorUv)
        val extras = FloatArray(MaterialUniformLayouts.PbrTexturedMaterial.total) { 0.5f }
        val drawA = RenderDrawCommand(mesh, stubMaterial, Mat4().translate(1f, 0f, 0f), extraUniformFloats = extras)
        val drawB = RenderDrawCommand(mesh, stubMaterial, Mat4().translate(2f, 0f, 0f), extraUniformFloats = extras)
        val members = listOf(drawA, drawB)
        val sharedOut = FloatArray(extras.size)
        val vec4 = Vec4(1f, 2f, 3f, 4f)
        val vecOut = FloatArray(4)

        // First frame grows internal buffers before steady-state measurement.
        frameInputs.update(viewProjection, eye, forward, lightPayload, cascades, Color.Black, 0.01f, GpuDebugView.Off, 1f)
        drawInputs.record(drawA, 1, MaterialUniformLayouts.PbrTexturedDraw.total, alphaToCoverage = false)

        val reading = readAllocation(unitsPerWindow = ITERATIONS) {
            for (i in 0 until ITERATIONS) {
                intSink += frameInputs.update(
                    viewProjection,
                    eye,
                    forward,
                    lightPayload,
                    cascades,
                    Color.Black,
                    0.01f,
                    GpuDebugView.Off,
                    1f,
                )
                drawInputs.record(drawA, i, MaterialUniformLayouts.PbrTexturedDraw.total, alphaToCoverage = false)
                if (InstanceTints.allAlike(members)) intSink += 1
                sink = InstanceTints.whiteTinted(extras, sharedOut)
                intSink += vecOut.putVec4(0, vec4)
            }
        }
        assertAllocatesNothing(reading, "FrameUniformInputs.update, DrawUniformInputs.record, InstanceTints, and putVec4")
    }

    private companion object {
        const val ITERATIONS = 1_000
        val LEGACY_FACTOR_FLOATS =
            UniformFields.PbrFactors.floats + UniformFields.BaseColorFactor.floats + UniformFields.EmissiveFactor.floats

        fun stubMesh(vertexFormat: VertexFormat) = object : Mesh {
            override val format = vertexFormat
            override val sizeBytes = 0L
            override fun destroy() = Unit
        }

        val stubMaterial = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }
    }
}
