/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.pipeline.CullMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class InstanceBatchingTest {
    private val mesh = mesh(VertexFormat.PositionNormalColorUv)
    private val material = material()

    @Test
    fun copiesOfOneMeshAndMaterialBecomeOneInstancedDraw() {
        val draws = (0 until 3).map { RenderDrawCommand(mesh, material, model = at(it.toFloat()), extraUniformFloats = floatArrayOf(1f)) }

        val batched = batchInstances(draws) { true }

        val draw = batched.single()
        assertEquals(draws.map { it.model }, draw.instanceModels)
        assertSame(mesh, draw.mesh)
        assertEquals(1f, draw.extraUniformFloats.single())
    }

    @Test
    fun drawsThatDifferInMoreThanPlacementStayApart() {
        val base = RenderDrawCommand(mesh, material, extraUniformFloats = floatArrayOf(1f))
        val draws = listOf(
            base,
            base.copy(model = at(1f)),
            base.copy(extraUniformFloats = floatArrayOf(2f)),
            base.copy(material = material()),
            base.copy(cullMode = CullMode.Back),
            base.copy(transparent = true),
            base.copy(shadowsOnly = true),
        )

        val batched = batchInstances(draws) { true }

        assertEquals(6, batched.size)
        assertEquals(2, batched.single { it.instanceModels != null }.instanceModels!!.size)
    }

    @Test
    fun aFormatTheBackendCannotInstanceIsLeftAlone() {
        val draws = List(4) { RenderDrawCommand(mesh, material, model = at(it.toFloat())) }

        assertEquals(draws, batchInstances(draws) { it != VertexFormat.PositionNormalColorUv })
    }

    @Test
    fun aGroupLargerThanOneDrawHoldsIsSplit() {
        val draws = List(MAX_BATCHED_INSTANCES + 1) { RenderDrawCommand(mesh, material, model = at(it.toFloat())) }

        val sizes = batchInstances(draws) { true }.map { it.instanceModels?.size ?: 1 }

        assertEquals(listOf(MAX_BATCHED_INSTANCES, 1), sizes)
    }

    @Test
    fun anInstancedDrawIsBoundedByAllItsCopiesUnlessOneIsUnbounded() {
        val box = { x: Float -> Aabb(Vec3f(x, 0f, 0f), Vec3f(x + 1f, 1f, 1f)) }
        val bounded = List(2) { RenderDrawCommand(mesh, material, model = at(it.toFloat()), worldBounds = box(it * 10f)) }

        assertEquals(Aabb(Vec3f(0f, 0f, 0f), Vec3f(11f, 1f, 1f)), batchInstances(bounded) { true }.single().worldBounds)
        assertNull(batchInstances(bounded + bounded[0].copy(worldBounds = null)) { true }.single().worldBounds)
    }

    private fun at(x: Float) = Mat4().translate(x, 0f, 0f)

    private fun mesh(format: VertexFormat) = object : Mesh {
        override val format = format
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun material() = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
