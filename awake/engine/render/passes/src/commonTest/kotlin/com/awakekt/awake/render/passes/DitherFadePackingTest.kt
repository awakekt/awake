/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.sceneLightUniforms
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformLayout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** A draw's fade reaches its shader as `1 - fadeOpacity` in `Exposure.y`, and leaves an unfaded draw's block as it was. */
class DitherFadePackingTest {
    private class FakeMesh(override val format: VertexFormat) : Mesh {
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private class FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private fun pack(format: VertexFormat, layout: UniformLayout, opacity: Float): FloatArray =
        RenderDrawCommand(FakeMesh(format), FakeMaterial()).apply { fadeOpacity = opacity }.uniformFloats(
            materialUniformFloatCount = layout.total,
            viewProjection = Mat4(),
            cameraEye = Vec3f(0f, 0f, 5f),
            lightPayload = sceneLightUniforms(DEFAULT_SCENE_LIGHT, Vec3f.ZERO).packed,
            cameraForward = Vec3f(0f, 0f, -1f),
        )

    @Test
    fun aFadedDrawCarriesItsFadeInExposureY() {
        for ((format, layout) in PLANS) {
            val packed = pack(format, layout, opacity = 0.35f)
            assertEquals(0.65f, packed[layout.offsetOf(UniformFields.Exposure) + 1], 1e-6f, "$format")
            assertEquals(1f, packed[layout.offsetOf(UniformFields.Exposure)], "exposure itself is untouched for $format")
        }
    }

    @Test
    fun anOpaqueDrawPacksExactlyAsBefore() {
        for ((format, layout) in PLANS) {
            val unfaded = pack(format, layout, opacity = 1f)
            assertEquals(0f, unfaded[layout.offsetOf(UniformFields.Exposure) + 1], "$format")
            assertContentEquals(unfaded, pack(format, layout, opacity = 1f))
        }
    }

    @Test
    fun drawsThatFadeDifferentlyAreNotFoldedIntoOneInstance() {
        val mesh = FakeMesh(VertexFormat.PositionNormalColor)
        val material = FakeMaterial()
        val opaque = RenderDrawCommand(mesh, material)
        val faded = RenderDrawCommand(mesh, material).apply { fadeOpacity = 0.5f }

        assertEquals(2, batchInstances(listOf(opaque, faded)) { _, _ -> true }.size)
    }

    @Test
    fun reusingARequestPutsItsFadeBack() {
        val draw = RenderDrawCommand(FakeMesh(VertexFormat.PositionNormalColor), FakeMaterial()).apply { fadeOpacity = 0.2f }
        draw.set(draw.mesh, draw.material, Mat4(), vertexAnimation = Vec3f.ZERO)

        assertEquals(1f, draw.fadeOpacity)
    }

    private companion object {
        val PLANS = listOf(
            VertexFormat.PositionNormalColor to MaterialUniformLayouts.LitShadow,
            VertexFormat.PositionNormalColorUv to MaterialUniformLayouts.PbrTextured,
        )
    }
}
