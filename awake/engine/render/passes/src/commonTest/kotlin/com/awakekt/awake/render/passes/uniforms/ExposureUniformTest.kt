/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.instancedUniformFloats
import com.awakekt.awake.render.passes.uniformFloats
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The environment's exposure reaches the `exposure` field of every lit block the shared packer writes. */
class ExposureUniformTest {

    @Test
    fun theEnvironmentsExposureReachesEveryLitBlock() {
        val exposure = EnvironmentUniforms(exposure = 2.5f).toGpuState(viewDepthRange = 50f).exposure
        val lit = draw(VertexFormat.PositionNormalColor)
        val textured = draw(VertexFormat.PositionNormalColorUv)
        val light = sceneLightUniforms(DEFAULT_SCENE_LIGHT, Vec3f.ZERO).packed

        fun single(command: RenderDrawCommand, layout: UniformLayout) = command.uniformFloats(
            materialUniformFloatCount = layout.total,
            viewProjection = Mat4(),
            cameraEye = Vec3f.ZERO,
            lightPayload = light,
            cameraForward = Vec3f(0f, 0f, -1f),
            exposure = exposure,
        )
        fun instanced(command: RenderDrawCommand, layout: UniformLayout) = command.copy(instanceModels = listOf(Mat4()))
            .instancedUniformFloats(
                kind = InstancedDrawKind.Plain,
                viewProjection = Mat4(),
                lightPayload = light,
                materialUniformFloatCount = layout.total,
                cameraForward = Vec3f(0f, 0f, -1f),
                exposure = exposure,
            )

        val litShadow = MaterialUniformLayouts.LitShadow
        val pbrTextured = MaterialUniformLayouts.PbrTextured
        assertEquals(2.5f, litShadow.readVec4(single(lit, litShadow), UniformFields.Exposure).x)
        assertEquals(2.5f, pbrTextured.readVec4(single(textured, pbrTextured), UniformFields.Exposure).x)
        assertEquals(2.5f, litShadow.readVec4(instanced(lit, litShadow), UniformFields.Exposure).x)
        assertEquals(2.5f, pbrTextured.readVec4(instanced(textured, pbrTextured), UniformFields.Exposure).x)

        // The display-referred blocks too: skinned, and instanced without the lit_shadow block.
        val skinned = draw(VertexFormat.PositionNormalColorSkin)
        assertEquals(2.5f, SkinnedUniformLayout.readVec4(single(skinned, SkinnedUniformLayout), UniformFields.Exposure).x)
        assertEquals(2.5f, InstancedUniformLayout.readVec4(instanced(lit, InstancedUniformLayout), UniformFields.Exposure).x)
    }

    @Test
    fun anExposureMustBeFiniteAndAboveZero() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { exposure ->
            assertFailsWith<IllegalArgumentException>("exposure $exposure") { EnvironmentUniforms(exposure = exposure) }
        }
    }

    private fun draw(format: VertexFormat) = RenderDrawCommand(
        mesh = object : com.awakekt.awake.render.mesh.Mesh {
            override val format = format
            override val sizeBytes: Long = 0
            override fun destroy() = Unit
        },
        material = object : com.awakekt.awake.render.material.Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        },
    )
}
