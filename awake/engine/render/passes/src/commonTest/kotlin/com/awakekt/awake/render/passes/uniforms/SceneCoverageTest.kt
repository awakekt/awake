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
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.UniformFields
import kotlin.test.Test
import kotlin.test.assertEquals

class SceneCoverageTest {
    @Test
    fun maskedCutoffEndpointsStayDistinctAndDepthKeepsItsHardCutoff() {
        val draw = draw(VertexFormat.PositionNormalColorUv)
        draw.alphaMode = AlphaMode.Masked
        for (cutoff in listOf(0f, 0.5f, 1f)) {
            draw.alphaCutoff = cutoff
            assertEquals(cutoff, draw.sceneCoverageCutoff(false))
            assertEquals(cutoff, draw.coverageCutoff)
            assertEquals(-(1f + cutoff), draw.sceneCoverageCutoff(true))
        }
        draw.alphaMode = AlphaMode.Opaque
        assertEquals(SceneCoverage.Opaque, draw.sceneCoverageCutoff(true))
        draw.transparent = true
        assertEquals(0f, draw.sceneCoverageCutoff(true))
    }

    @Test
    fun ordinaryInstancedAndSkinnedPackTheSameCoveragePolicy() {
        val textured = draw(VertexFormat.PositionNormalColorUv).apply { alphaMode = AlphaMode.Masked }
        val skinned = draw(VertexFormat.PositionNormalColorUvSkin).apply { alphaMode = AlphaMode.Masked }
        for ((draw, layout) in listOf(textured to MaterialUniformLayouts.PbrTextured, skinned to SkinnedUniformLayout)) {
            val block = draw.uniformFloats(
                materialUniformFloatCount = layout.total,
                viewProjection = Mat4(),
                cameraEye = Vec3f.ZERO,
                lightPayload = sceneLightUniforms(DEFAULT_SCENE_LIGHT, Vec3f.ZERO).packed,
                cameraForward = Vec3f(0f, 0f, -1f),
                alphaToCoverageEnabled = true,
            )
            assertEquals(-1.5f, block[layout.offsetOf(UniformFields.PbrFactors) + 2])
        }
        val instanced = textured.instancedUniformFloats(
            kind = InstancedDrawKind.Plain,
            viewProjection = Mat4(),
            lightPayload = sceneLightUniforms(DEFAULT_SCENE_LIGHT, Vec3f.ZERO).packed,
            materialUniformFloatCount = MaterialUniformLayouts.PbrTextured.total,
            cameraForward = Vec3f(0f, 0f, -1f),
            alphaToCoverageEnabled = true,
        )
        assertEquals(-1.5f, instanced[MaterialUniformLayouts.PbrTextured.offsetOf(UniformFields.PbrFactors) + 2])
    }

    private fun draw(format: VertexFormat) = RenderDrawCommand(
        mesh = object : com.awakekt.awake.render.mesh.Mesh {
            override val format = format
            override val sizeBytes = 0L
            override fun destroy() = Unit
        },
        material = object : com.awakekt.awake.render.material.Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        },
    )
}
