/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.ShadowCascadeUniforms
import com.awakekt.awake.render.renderer.UniformFields
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
        val drawCall = RenderDrawCommand(
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

    /** A payload from before texture animation keeps its factors and plays a still texture. */
    @Test
    fun testPbrTexturedMaterialFloatsSupplied() {
        val custom = FloatArray(12) { it.toFloat() }
        val drawCall = RenderDrawCommand(
            mesh = FakeMesh(),
            material = FakeMaterial(),
            model = Mat4(),
            extraUniformFloats = custom,
        )
        val floats = pbrTexturedMaterialFloats(drawCall)
        assertEquals(PBR_TEXTURED_MATERIAL_FLOATS, floats.size)
        assertEquals(0f, floats[0])
        assertEquals(11f, floats[11])
        assertVec4(1f, 1f, 0f, 1f, MaterialUniformLayouts.PbrTexturedMaterial.readVec4(floats, UniformFields.TextureFrames))
    }

    @Test
    fun textureAnimationAndTheDrawsTimeReachTheTexturedBlock() {
        val animation = TextureAnimation(columns = 8, rows = 7, frameCount = 54, framesPerSecond = 12f, scrollU = 0.25f, scrollV = -0.5f)
        val payload = pbrMaterialFloats(0f, 1f, Color.White, Color.Transparent, animation)
        val drawCall = RenderDrawCommand(
            mesh = FakeMesh(),
            material = FakeMaterial(),
            extraUniformFloats = payload,
            timeSeconds = 3.5f,
        )

        val material = pbrTexturedMaterialFloats(drawCall)
        val layout = MaterialUniformLayouts.PbrTexturedMaterial
        assertVec4(8f, 7f, 12f, 54f, layout.readVec4(material, UniformFields.TextureFrames))
        assertVec4(0.25f, -0.5f, 3.5f, 0f, layout.readVec4(material, UniformFields.TextureScroll))

        // The explicit writer the backends call lands the same values in the full block.
        val block = texturedUniforms(
            mvp = Mat4(),
            model = Mat4(),
            lightPayload = FloatArray(
                listOf(UniformFields.LightDirection, UniformFields.LightColor, UniformFields.PointLightPositions, UniformFields.PointLightColors)
                    .sumOf { it.floats },
            ),
            extraUniformFloats = payload,
            cameraEye = com.awakekt.awake.core.math.Vec3f(0f, 0f, 0f),
            fogColor = Color.Black,
            fogDensity = 0f,
            timeSeconds = 3.5f,
        )
        val full = MaterialUniformLayouts.PbrTextured
        assertVec4(8f, 7f, 12f, 54f, full.readVec4(block, UniformFields.TextureFrames))
        assertVec4(0.25f, -0.5f, 3.5f, 0f, full.readVec4(block, UniformFields.TextureScroll))
    }

    @Test
    fun aFrameSheetMustHoldItsFrames() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { TextureAnimation(columns = 2, rows = 2, frameCount = 5) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { TextureAnimation(framesPerSecond = -1f) }
    }

    private fun assertVec4(x: Float, y: Float, z: Float, w: Float, actual: com.awakekt.awake.core.math.Vec4) {
        assertEquals(listOf(x, y, z, w), listOf(actual.x, actual.y, actual.z, actual.w))
    }

    @Test
    fun texturedMaterialFloatsReserveCutoffForMaskedDepth() {
        val drawCall = RenderDrawCommand(
            mesh = FakeMesh(),
            material = FakeMaterial(),
            alphaCutoff = 0.37f,
            extraUniformFloats = FloatArray(12) { it.toFloat() },
        )

        assertEquals(0.37f, pbrTexturedMaterialFloats(drawCall)[2])
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
        // pbr(4) + baseColor(4) + emissive(4) + textureFrames(4) + textureScroll(4)
        assertEquals(20, PBR_TEXTURED_MATERIAL_FLOATS)

        // Lit: MVP(16) + lightDir(4) + lightColor(4) + pbr(4) = 28 floats
        assertEquals(28, MaterialUniformLayouts.Lit.total)

        // PbrTextured: MVP(16) + lightDir(4) + lightColor(4) + model(16) + camPos(4) + pbr(4) + baseColor(4) + emissive(4) + fog(4) + debugView(4) = 64 floats
        // 64 + 32: the PBR path gained MAX_POINT_LIGHTS slots in two vec4 arrays; + 8 for texture animation;
        // + 84 for the sun's cascades (4 matrices, 4 depth scales, camera forward).
        assertEquals(188, MaterialUniformLayouts.PbrTextured.total)
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
    fun aScenesAmbientRidesInTheColourPadSlot() {
        val light = SceneLight(direction = Vec3f(1f, 2f, 3f), color = Vec3f(4f, 5f, 6f), ambient = 0.5f)
        // Index 7, lightColor.w; 0 there (no ambient set) tells every shader to keep its own.
        assertEquals(0.5f, sceneLightFloats(light)[7])
        assertEquals(0f, sceneLightFloats(light.copy(ambient = null))[7])
    }

    @Test
    fun litShadowPacksVertexAnimationParametersAndFrameTime() {
        val drawCall = RenderDrawCommand(
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
            cameraForward = Vec3f(0f, 0f, -1f),
        )

        val packed = litShadowUniforms(
            drawCall,
            Mat4(),
            ShadowCascadeUniforms(listOf(Mat4()), floatArrayOf(Float.MAX_VALUE)),
            frame,
        )
        val offset = MaterialUniformLayouts.LitShadow.offsetOf(UniformFields.VertexAnimation)

        assertEquals(0.08f, packed[offset])
        assertEquals(6f, packed[offset + 1])
        assertEquals(0.8f, packed[offset + 2])
        assertEquals(3.5f, packed[offset + 3])
        val forwardOffset = MaterialUniformLayouts.LitShadow.offsetOf(UniformFields.CameraForward)
        assertContentEquals(floatArrayOf(0f, 0f, -1f, 1f), packed.copyOfRange(forwardOffset, forwardOffset + 4))
    }

    @Test
    fun gpuLitShadowPackerMatchesSceneLitShadowAbi() {
        val model = Mat4().apply { identity() }.translate(1f, 2f, 3f)
        val animation = Vec3f(0.08f, 6f, 0.8f)
        val light = sceneLightUniforms(
            SceneLight(direction = Vec3f.UP, color = Vec3f.ONE),
            eye = Vec3f(4f, 5f, 6f),
        )
        val cascades = ShadowCascadeUniforms(
            viewProjections = listOf(Mat4()),
            splitDistances = floatArrayOf(Float.MAX_VALUE),
        )
        val draw = RenderDrawCommand(
            mesh = FakeMesh(),
            material = FakeMaterial(),
            model = model,
            vertexAnimation = animation,
            timeSeconds = 3.5f,
        )
        val frame = SceneFrameUniforms(
            light = light,
            cameraEye = Vec3f(4f, 5f, 6f),
            fog = floatArrayOf(0.1f, 0.2f, 0.3f, 0.04f),
            cameraForward = Vec3f(0f, -1f, 0f),
        )
        assertContentEquals(
            litShadowUniforms(draw, model, cascades, frame),
            gpuLitShadowUniforms(
                transform = model,
                extraUniformFloats = draw.extraUniformFloats,
                vertexAnimation = animation,
                timeSeconds = draw.timeSeconds,
                mvp = model,
                lightPayload = light.packed,
                cascades = cascades,
                cameraEye = frame.cameraEye,
                cameraForward = frame.cameraForward,
                fogColor = Color(0.1f, 0.2f, 0.3f),
                fogDensity = 0.04f,
            ),
        )
    }
}
