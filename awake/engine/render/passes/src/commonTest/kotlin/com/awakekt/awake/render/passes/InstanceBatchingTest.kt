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
import com.awakekt.awake.core.math.Vec4
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

        val batched = batchInstances(draws) { _, _ -> true }

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
            base.copy(maskLayer = 0),
        )

        val batched = batchInstances(draws) { _, _ -> true }

        assertEquals(7, batched.size)
        assertEquals(2, batched.single { it.instanceModels != null }.instanceModels!!.size)
    }

    @Test
    fun backCulledCopiesFoldOnlyWhereTheBackendCanInstanceThemBackCulled() {
        val draws = List(3) { RenderDrawCommand(mesh, material, model = at(it.toFloat()), cullMode = CullMode.Back) }

        val folded = batchInstances(draws) { _, _ -> true }
        val apart = batchInstances(draws) { _, cullMode -> cullMode == CullMode.None }

        assertEquals(CullMode.Back, folded.single().cullMode, "the batch keeps the copies' culling")
        assertEquals(draws, apart)
    }

    @Test
    fun copiesThatCullDifferentlyStayInSeparateBatches() {
        val draws = List(4) { RenderDrawCommand(mesh, material, model = at(it.toFloat()), cullMode = if (it < 2) CullMode.None else CullMode.Back) }

        val batched = batchInstances(draws) { _, _ -> true }

        assertEquals(listOf(CullMode.None, CullMode.Back), batched.map { it.cullMode })
        assertEquals(listOf(2, 2), batched.map { it.instanceModels!!.size })
    }

    @Test
    fun aFormatTheBackendCannotInstanceIsLeftAlone() {
        val draws = List(4) { RenderDrawCommand(mesh, material, model = at(it.toFloat())) }

        assertEquals(draws, batchInstances(draws) { format, _ -> format != VertexFormat.PositionNormalColorUv })
    }

    @Test
    fun aGroupLargerThanOneDrawHoldsIsSplit() {
        val draws = List(MAX_BATCHED_INSTANCES + 1) { RenderDrawCommand(mesh, material, model = at(it.toFloat())) }

        val sizes = batchInstances(draws) { _, _ -> true }.map { it.instanceModels?.size ?: 1 }

        assertEquals(listOf(MAX_BATCHED_INSTANCES, 1), sizes)
    }

    @Test
    fun anInstancedDrawIsBoundedByAllItsCopiesUnlessOneIsUnbounded() {
        val box = { x: Float -> Aabb(Vec3f(x, 0f, 0f), Vec3f(x + 1f, 1f, 1f)) }
        val bounded = List(2) { RenderDrawCommand(mesh, material, model = at(it.toFloat()), worldBounds = box(it * 10f)) }

        assertEquals(Aabb(Vec3f(0f, 0f, 0f), Vec3f(11f, 1f, 1f)), batchInstances(bounded) { _, _ -> true }.single().worldBounds)
        assertNull(batchInstances(bounded + bounded[0].copy(worldBounds = null)) { _, _ -> true }.single().worldBounds)
    }

    @Test
    fun anAuthoredInstanceListLargerThanOneDrawHoldsIsSplitWithItsPerInstanceData() {
        val count = MAX_BATCHED_INSTANCES + 10
        val models = List(count) { at(it.toFloat()) }
        val colors = List(count) { Vec4(it.toFloat(), 0f, 0f, 1f) }

        val split = batchInstances(listOf(RenderDrawCommand(mesh, material, instanceModels = models, instanceColors = colors))) { _, _ -> true }

        assertEquals(listOf(MAX_BATCHED_INSTANCES, 10), split.map { it.instanceModels!!.size })
        assertEquals(models.drop(MAX_BATCHED_INSTANCES), split[1].instanceModels)
        assertEquals(colors.drop(MAX_BATCHED_INSTANCES), split[1].instanceColors, "per-instance data stays index-aligned")
    }

    @Test
    fun aSkinnedInstanceListSplitsAtThePaletteBufferSize() {
        val count = MAX_SKINNED_BATCHED_INSTANCES * 2 + 1
        val draw = RenderDrawCommand(
            mesh,
            material,
            instanceModels = List(count) { at(it.toFloat()) },
            instanceJointPalettes = List(count) { floatArrayOf(it.toFloat()) },
            instanceColors = List(count) { Vec4(it.toFloat(), 0f, 0f, 1f) },
        )

        val batches = batchInstances(listOf(draw)) { _, _ -> true }
        val sizes = batches.map { it.instanceJointPalettes!!.size }

        assertEquals(listOf(MAX_SKINNED_BATCHED_INSTANCES, MAX_SKINNED_BATCHED_INSTANCES, 1), sizes)
        for (batch in batches) {
            assertEquals(batch.instanceJointPalettes!!.map { it[0] }, batch.instanceColors!!.map { it.x })
            assertEquals(batch.instanceModels!!.size, batch.instanceColors!!.size)
        }
    }

    @Test
    fun interleavedCopiesStillFoldByWhatTheyDraw() {
        val other = material()
        val draws = List(6) { RenderDrawCommand(mesh, if (it % 2 == 0) material else other, model = at(it.toFloat())) }

        val batched = batchInstances(draws) { _, _ -> true }

        assertEquals(2, batched.size)
        assertEquals(listOf(draws[0].model, draws[2].model, draws[4].model), batched.single { it.material === material }.instanceModels)
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
