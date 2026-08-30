/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.passes.uniforms

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.render.renderer.DrawCall
import io.github.awakelab.awake.render.renderer.SceneLight
import io.github.awakelab.awake.render.renderer.UniformFields
import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialUniformsTest {

    private class FakeMesh : Mesh {
        override val format: VertexFormat = VertexFormat.PositionColorUv
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private class FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    @Test
    fun testPbrMaterialFloatsDefault() {
        val drawCall = DrawCall(
            mesh = FakeMesh(),
            material = FakeMaterial(),
            model = Mat4(),
            extraUniformFloats = floatArrayOf(),
        )
        val floats = pbrMaterialFloats(drawCall)
        assertEquals(4, floats.size)
        assertEquals(0f, floats[0])
        assertEquals(0.5f, floats[1])
    }

    @Test
    fun testPbrTexturedMaterialFloatsSupplied() {
        val custom = FloatArray(12) { it.toFloat() }
        val drawCall = DrawCall(
            mesh = FakeMesh(),
            material = FakeMaterial(),
            model = Mat4(),
            extraUniformFloats = custom,
        )
        val floats = pbrTexturedMaterialFloats(drawCall)
        assertEquals(12, floats.size)
        assertEquals(0f, floats[0])
        assertEquals(11f, floats[11])
    }

    @Test
    fun testFogUniformFloats() {
        val fog = fogUniformFloats(Color(r = 0.5f, g = 0.6f, b = 0.7f), 0.05f)
        assertEquals(4, fog.size)
        assertEquals(0.5f, fog[0])
        assertEquals(0.6f, fog[1])
        assertEquals(0.7f, fog[2])
        assertEquals(0.05f, fog[3])
    }

    @Test
    fun testMaterialUniformLayouts() {
        assertEquals(4, PBR_MATERIAL_FLOATS)
        assertEquals(12, PBR_TEXTURED_MATERIAL_FLOATS)

        // Lit: MVP(16) + lightDir(4) + lightColor(4) + pbr(4) = 28 floats
        assertEquals(28, MaterialUniformLayouts.Lit.total)

        // PbrTextured: MVP(16) + lightDir(4) + lightColor(4) + model(16) + camPos(4) + pbr(4) + baseColor(4) + emissive(4) + fog(4) = 60 floats
        // 60 + 32: the PBR path gained MAX_POINT_LIGHTS slots in two vec4 arrays.
        assertEquals(92, MaterialUniformLayouts.PbrTextured.total)
    }

    @Test
    fun sceneLightPacksDirectionThenColorAcrossTwoVec4Slots() {
        val light = SceneLight(direction = Vec3f(1f, 2f, 3f), color = Vec3f(4f, 5f, 6f))
        assertEquals(
            listOf(1f, 2f, 3f, 0f, 4f, 5f, 6f, 0f),
            sceneLightFloats(light).toList(),
        )
    }

    @Test
    fun sceneLightShadowScaleRidesInTheDirectionPadSlot() {
        val light = SceneLight(direction = Vec3f(1f, 2f, 3f), color = Vec3f(4f, 5f, 6f))
        // Index 3, not 7: the shadow shader reads lightDirection.w, and swapping the two pad
        // slots would still be 8 floats and still look right everywhere shadows are off.
        assertEquals(0.25f, sceneLightFloats(light, shadowTexelDepthScale = 0.25f)[3])
        assertEquals(0f, sceneLightFloats(light, shadowTexelDepthScale = 0.25f)[7])
    }

    @Test
    fun litShadowPacksVertexAnimationParametersAndFrameTime() {
        val drawCall = DrawCall(
            mesh = FakeMesh(),
            material = FakeMaterial(),
            vertexAnimation = Vec3f(0.08f, 6f, 0.8f),
            timeSeconds = 3.5f,
        )
        val frame = SceneFrameUniforms(
            light = sceneLightUniforms(
                SceneLight(direction = Vec3f.UP, color = Vec3f.ONE),
                eye = Vec3f.ZERO,
            ),
            cameraEye = Vec3f.ZERO,
            fog = floatArrayOf(0f, 0f, 0f, 0f),
        )

        val packed = litShadowUniforms(drawCall, Mat4(), Mat4(), frame)
        val offset = MaterialUniformLayouts.LitShadow.offsetOf(UniformFields.VertexAnimation)

        assertEquals(0.08f, packed[offset])
        assertEquals(6f, packed[offset + 1])
        assertEquals(0.8f, packed[offset + 2])
        assertEquals(3.5f, packed[offset + 3])
    }
}
