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
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.FrameUniformBlock
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.render.passes.uniforms.drawBlockFloats
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformLayout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The frame block and a draw's own block hold, between them, what the single block held: every
 * field a lit shader read from [MaterialUniformLayouts.LitShadow] or `PbrTextured` is read from one
 * of the two with the same floats, and the product the shader now computes is the MVP it was given.
 */
class FrameUniformsTest {
    @Test
    fun theFrameBlockHoldsTheSingleBlocksFrameFields() {
        val frame = FrameUniformBlock().pack(VIEW_PROJECTION, EYE, FORWARD, LIGHT, CASCADES, FOG, FOG_DENSITY, GpuDebugView.Off, EXPOSURE)
        val whole = lit().whole(MaterialUniformLayouts.LitShadow.total)

        assertEquals(MaterialUniformLayouts.Frame.total, frame.size)
        assertContentEquals(VIEW_PROJECTION.data, frame.field(MaterialUniformLayouts.Frame, UniformFields.ViewProjection))
        FRAME_FIELDS.forEach { field ->
            assertContentEquals(
                whole.field(MaterialUniformLayouts.LitShadow, field),
                frame.field(MaterialUniformLayouts.Frame, field),
                "${field.name} moved to the frame block with other floats",
            )
        }
        assertEquals(EXPOSURE, frame[MaterialUniformLayouts.Frame.offsetOf(UniformFields.Exposure)])
    }

    @Test
    fun aLitDrawsBlockHoldsTheSingleBlocksDrawFields() {
        val draw = lit(fade = 0.25f)
        val own = draw.drawBlockFloats(MaterialUniformLayouts.LitShadowDraw.total, alphaToCoverageEnabled = false)
        val whole = draw.whole(MaterialUniformLayouts.LitShadow.total)

        listOf(UniformFields.Model, UniformFields.VertexAnimation, UniformFields.Material).forEach { field ->
            assertContentEquals(
                whole.field(MaterialUniformLayouts.LitShadow, field),
                own.field(MaterialUniformLayouts.LitShadowDraw, field),
                field.name,
            )
        }
        assertEquals(
            whole[MaterialUniformLayouts.LitShadow.offsetOf(UniformFields.Exposure) + 1],
            own[MaterialUniformLayouts.LitShadowDraw.offsetOf(UniformFields.DrawFade)],
            "the dither fade",
        )
    }

    @Test
    fun aTexturedDrawsBlockHoldsTheSingleBlocksDrawFields() {
        val draw = textured(fade = 0.5f)
        val own = draw.drawBlockFloats(MaterialUniformLayouts.PbrTexturedDraw.total, alphaToCoverageEnabled = false)
        val whole = draw.whole(MaterialUniformLayouts.PbrTextured.total)

        TEXTURED_DRAW_FIELDS.forEach { field ->
            assertContentEquals(
                whole.field(MaterialUniformLayouts.PbrTextured, field),
                own.field(MaterialUniformLayouts.PbrTexturedDraw, field),
                field.name,
            )
        }
        assertEquals(
            whole[MaterialUniformLayouts.PbrTextured.offsetOf(UniformFields.Exposure) + 1],
            own[MaterialUniformLayouts.PbrTexturedDraw.offsetOf(UniformFields.DrawFade)],
            "the dither fade",
        )
    }

    /** Depth passes read these two from the start of whichever draw block the buffer holds. */
    @Test
    fun everySplitDrawBlockStartsWithWhatADepthPassReads() {
        listOf(MaterialUniformLayouts.LitShadowDraw, MaterialUniformLayouts.PbrTexturedDraw).forEach { layout ->
            assertEquals(listOf(UniformFields.Model, UniformFields.VertexAnimation), layout.fields.take(2))
        }
    }

    @Test
    fun anInstancedDrawsBlockLeavesTheModelToItsInstances() {
        val own = lit().drawBlockFloats(MaterialUniformLayouts.LitShadowDraw.total, alphaToCoverageEnabled = false, instanced = true)
        assertContentEquals(Mat4().data, own.field(MaterialUniformLayouts.LitShadowDraw, UniformFields.Model))
    }

    @Test
    fun theFrameBlockIsRefilledInPlace() {
        val block = FrameUniformBlock()
        val first = block.pack(VIEW_PROJECTION, EYE, FORWARD, LIGHT, CASCADES, FOG, FOG_DENSITY, GpuDebugView.Off, EXPOSURE)
        val second = block.pack(Mat4(), Vec3f(1f, 2f, 3f), FORWARD, LIGHT, null, FOG, 0f, GpuDebugView.Off, 1f)
        assertSame(first, second, "a frame allocates no block")
    }

    private fun RenderDrawCommand.whole(count: Int): FloatArray = uniformFloats(
        materialUniformFloatCount = count,
        viewProjection = VIEW_PROJECTION,
        cameraEye = EYE,
        lightPayload = LIGHT,
        shadowCascades = CASCADES,
        fogColor = FOG,
        fogDensity = FOG_DENSITY,
        cameraForward = FORWARD,
        exposure = EXPOSURE,
    )

    private fun FloatArray.field(layout: UniformLayout, field: UniformField): FloatArray =
        copyOfRange(layout.offsetOf(field), layout.offsetOf(field) + field.floats)

    private fun lit(fade: Float = 0f) = RenderDrawCommand(
        mesh = FakeMesh(VertexFormat.PositionNormalColor),
        material = FakeMaterial,
        model = MODEL,
        extraUniformFloats = floatArrayOf(0.2f, 0.7f, 0f, 0f),
        vertexAnimation = Vec3f(0.4f, 1f, 3f),
        timeSeconds = 2f,
    ).apply { fadeOpacity = 1f - fade }

    private fun textured(fade: Float = 0f) = RenderDrawCommand(
        mesh = FakeMesh(VertexFormat.PositionNormalColorUv),
        material = FakeMaterial,
        model = MODEL,
        extraUniformFloats = pbrMaterialFloats(0.1f, 0.6f, Color.White, Color.Transparent, TextureAnimation(columns = 2, rows = 2, framesPerSecond = 4f)),
        timeSeconds = 2f,
    ).apply { fadeOpacity = 1f - fade }

    private class FakeMesh(override val format: VertexFormat) : Mesh {
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val EXPOSURE = 1.5f
        const val FOG_DENSITY = 0.02f
        val FOG = Color(0.5f, 0.6f, 0.7f, 1f)
        val EYE = Vec3f(1f, 4f, 6f)
        val FORWARD = Vec3f(0f, -0.6f, -0.8f)
        val MODEL = Mat4().apply { data[12] = 2f; data[13] = 0.5f }
        val VIEW_PROJECTION = Mat4().apply { data[0] = 0.5f; data[14] = -3f }
        val LIGHT = FloatArray(MaterialUniformLayouts.SceneLight.total) { 0.1f * (it + 1) }
        val CASCADES = GpuShadowCascadeData(listOf(Mat4(), Mat4().apply { data[0] = 0.25f }), floatArrayOf(10f, 30f))
        val FRAME_FIELDS = listOf(
            UniformFields.LightDirection,
            UniformFields.LightColor,
            UniformFields.PointLightPositions,
            UniformFields.PointLightColors,
            UniformFields.CascadeViewProjections,
            UniformFields.CascadeDepthScales,
            UniformFields.CameraPosition,
            UniformFields.CameraForward,
            UniformFields.FogColor,
            UniformFields.DebugView,
        )
        val TEXTURED_DRAW_FIELDS = listOf(
            UniformFields.Model,
            UniformFields.VertexAnimation,
            UniformFields.PbrFactors,
            UniformFields.BaseColorFactor,
            UniformFields.EmissiveFactor,
            UniformFields.TextureFrames,
            UniformFields.TextureScroll,
        )
    }
}
