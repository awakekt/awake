/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.pipeline.CullMode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Copies differing only in tint and fade fold into one draw where the pipeline reads them per instance. */
class InstanceTintFoldTest {
    private val mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColorUv
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }
    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
    private val canInstance = { _: VertexFormat, _: CullMode -> true }
    private val tints = { _: VertexFormat, _: CullMode -> true }
    private val noTints = { _: VertexFormat, _: CullMode -> false }

    @Test
    fun tintedAndFadingCopiesFoldCarryingEachOnesTintAndFade() {
        val draws = listOf(prop(Color(1f, 0f, 0f), fade = 1f), prop(Color(0f, 1f, 0f), fade = 0.5f), prop(Color(0f, 0f, 1f), fade = 1f))

        val draw = InstanceBatcher().batch(draws, canInstance, tints).single()

        assertEquals(listOf(Vec4(1f, 0f, 0f, 1f), Vec4(0f, 1f, 0f, 0.5f), Vec4(0f, 0f, 1f, 1f)), draw.instanceColors)
        assertContentEquals(extras(Color.White), draw.extraUniformFloats, "the shared block's tint is white")
        assertEquals(1f, draw.fadeOpacity, "and each copy fades on its own")
    }

    @Test
    fun copiesThatAgreeFoldAsBeforeWithNoColours() {
        val draws = List(3) { prop(Color(0.5f, 0.5f, 0.5f), fade = 0.75f) }

        val draw = InstanceBatcher().batch(draws, canInstance, tints).single()

        assertNull(draw.instanceColors, "the shared block carries their one tint")
        assertContentEquals(extras(Color(0.5f, 0.5f, 0.5f)), draw.extraUniformFloats)
        assertEquals(0.75f, draw.fadeOpacity)
    }

    @Test
    fun aPipelineThatReadsNoTintKeepsTintedCopiesApart() {
        val draws = listOf(prop(Color(1f, 0f, 0f)), prop(Color(0f, 1f, 0f)), prop(Color(1f, 0f, 0f)))

        val batched = InstanceBatcher().batch(draws, canInstance, noTints)

        assertEquals(listOf(1, 2), batched.map { it.instanceModels?.size ?: 1 }.sorted())
    }

    @Test
    fun copiesThatDifferInTheirTintsAlphaStayApart() {
        val draws = listOf(prop(Color(1f, 1f, 1f, 1f)), prop(Color(1f, 1f, 1f, 0.5f)))

        assertEquals(2, InstanceBatcher().batch(draws, canInstance, tints).size, "the tint's alpha stays in the shared block")
    }

    private fun prop(tint: Color, fade: Float = 1f) =
        RenderDrawCommand(mesh, material, model = Mat4(), extraUniformFloats = extras(tint)).apply { fadeOpacity = fade }

    private fun extras(tint: Color) = pbrMaterialFloats(metallic = 0f, roughness = 1f, baseColorFactor = tint, emissiveFactor = Color.Transparent)
}
