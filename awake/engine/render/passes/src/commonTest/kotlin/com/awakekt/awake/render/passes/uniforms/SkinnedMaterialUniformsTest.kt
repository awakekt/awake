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
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniformFloats
import com.awakekt.awake.render.renderer.SkinnedFields
import com.awakekt.awake.render.renderer.SkinnedMaterialLayout
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformFields
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A skinned draw's block carries its material's factors next to its palette, and white and black without them. */
class SkinnedMaterialUniformsTest {
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    @Test
    fun aTintedSkinnedDrawCarriesItsFactorsAndItsPalette() {
        val tinted = block(skinnedMaterialFloats(identity, Color(1f, 0.2f, 0.1f, 0.5f), Color(0f, 0f, 1f, 1f)))
        val plain = block(identity)

        assertEquals(listOf(1f, 0.2f, 0.1f, 0.5f), tinted.vec4(UniformFields.BaseColorFactor))
        assertEquals(listOf(0f, 0f, 1f, 0f), tinted.vec4(UniformFields.EmissiveFactor))
        assertEquals(listOf(1f, 1f, 1f, 1f), plain.vec4(UniformFields.BaseColorFactor), "a palette alone draws untinted")
        assertEquals(listOf(0f, 0f, 0f, 0f), plain.vec4(UniformFields.EmissiveFactor))
        assertContentEquals(plain.palette(), tinted.palette(), "the tint leaves the pose alone")
    }

    /** The payload is rewritten in place every frame, so a shorter palette must not leave old joints behind. */
    @Test
    fun aReusedPayloadClearsJointsAShorterPaletteNoLongerHas() {
        val payload = skinnedMaterialFloats(identity + identity, Color.White, Color.Transparent)

        val again = skinnedMaterialFloats(identity, Color.White, Color.Transparent, into = payload)

        assertSame(payload, again)
        assertEquals(SkinnedMaterialLayout.total, again.size)
        assertContentEquals(FloatArray(16), again.copyOfRange(16, 32))
    }

    private fun block(extras: FloatArray): FloatArray = RenderDrawCommand(
        mesh = object : com.awakekt.awake.render.mesh.Mesh {
            override val format = VertexFormat.PositionNormalColorUvSkin
            override val sizeBytes: Long = 0
            override fun destroy() = Unit
        },
        material = object : com.awakekt.awake.render.material.Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        },
        extraUniformFloats = extras,
    ).uniformFloats(
        materialUniformFloatCount = SkinnedUniformLayout.total,
        viewProjection = Mat4(),
        cameraEye = Vec3f.ZERO,
        lightPayload = sceneLightUniforms(DEFAULT_SCENE_LIGHT, Vec3f.ZERO).packed,
        cameraForward = Vec3f(0f, 0f, -1f),
    )

    private fun FloatArray.vec4(field: UniformField): List<Float> =
        SkinnedUniformLayout.readVec4(this, field).let { listOf(it.x, it.y, it.z, it.w) }

    private fun FloatArray.palette(): FloatArray {
        val start = SkinnedUniformLayout.offsetOf(SkinnedFields.JointPalette)
        return copyOfRange(start, start + 16)
    }
}
