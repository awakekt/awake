/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.CullMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each reason [diagnoseInstanceFolds] gives for a draw that stays on its own, and the folds it counts. */
class InstanceFoldDiagnosisTest {
    private val textured = mesh(VertexFormat.PositionNormalColorUv)
    private val material = material()

    /** The project plan's shape: textured and plain formats instance unculled and back-culled, nothing else. */
    private val plan: (VertexFormat, CullMode) -> Boolean = { format, cullMode ->
        cullMode != CullMode.Front && (format == VertexFormat.PositionNormalColorUv || format == VertexFormat.PositionNormalColor)
    }

    @Test
    fun copiesThatFoldAreCountedAsFoldsNotMisses() {
        val stats = diagnose(copies(5))

        assertEquals(1, stats.foldedDraws)
        assertEquals(5, stats.foldedCopies)
        assertEquals(0, stats.totalMisses)
        assertEquals(5, stats.sourceDraws)
    }

    @Test
    fun aMeshDrawnOnceIsASingleCopy() {
        assertMisses(InstanceFoldMiss.SingleCopy, 1, listOf(draw()))
    }

    @Test
    fun blendedCopiesMissAsBlended() {
        assertMisses(InstanceFoldMiss.Blended, 3, copies(3) { it.copy(transparent = true) })
    }

    @Test
    fun maskedCopiesOnAFormatWithNoInstancedPipelineMissAsMasked() {
        val vertexColoured = mesh(VertexFormat.PositionColorUv)

        assertMisses(InstanceFoldMiss.Masked, 3, copies(3) { it.copy(mesh = vertexColoured, alphaMode = AlphaMode.Masked) })
    }

    @Test
    fun maskedCopiesFoldWhereTheirFormatInstances() {
        val stats = diagnose(copies(3) { it.copy(alphaMode = AlphaMode.Masked) })

        assertEquals(0, stats.totalMisses, "the textured instanced pipeline reads the cutoff, as the single one does")
        assertEquals(1, stats.foldedDraws)
    }

    @Test
    fun copiesOfAFormatWithNoInstancedPipelineMissAsVertexFormat() {
        assertMisses(InstanceFoldMiss.VertexFormat, 4, copies(4) { it.copy(mesh = mesh(VertexFormat.PositionColorUv)) })
    }

    @Test
    fun frontCulledCopiesMissAsCullMode() {
        assertMisses(InstanceFoldMiss.CullMode, 2, copies(2) { it.copy(cullMode = CullMode.Front) })
    }

    @Test
    fun copiesEachWithItsOwnMaterialMissAsDistinctMaterial() {
        assertMisses(InstanceFoldMiss.DistinctMaterial, 3, copies(3) { it.copy(material = material()) })
    }

    @Test
    fun copiesEachWithItsOwnUniformsMissAsDistinctUniforms() {
        var tint = 0f
        assertMisses(InstanceFoldMiss.DistinctUniforms, 3, copies(3) { it.copy(extraUniformFloats = floatArrayOf(tint++)) })
    }

    @Test
    fun posedSkinnedCopiesMissAsSkinnedNotInstanced() {
        val skinned = mesh(VertexFormat.PositionNormalColorUvSkin)

        assertMisses(InstanceFoldMiss.SkinnedNotInstanced, 3, copies(3) { it.copy(mesh = skinned) })
    }

    @Test
    fun authoredInstancedAndShadowOnlyDrawsAreNotMainPassMisses() {
        val authored = draw().copy(instanceModels = List(3) { Mat4() })
        val shadowOnly = draw().copy(shadowsOnly = true)

        val stats = diagnose(listOf(authored, shadowOnly))

        assertEquals(1, stats.authoredInstancedDraws)
        assertEquals(0, stats.totalMisses)
    }

    @Test
    fun theDiagnosisAgreesWithWhatBatchingDraws() {
        val draws = copies(4) + copies(2) { it.copy(transparent = true) } + draw().copy(mesh = mesh(VertexFormat.PositionNormalColor))

        val stats = diagnose(draws)
        val batched = batchInstances(draws, plan)

        assertEquals(batched.size, stats.foldedDraws + stats.totalMisses)
    }

    private fun assertMisses(reason: InstanceFoldMiss, count: Int, draws: List<RenderDrawCommand>) {
        val stats = diagnose(draws)
        assertEquals(count, stats.misses(reason), "$reason in $stats")
        assertEquals(count, stats.totalMisses, "no other reason in $stats")
        assertEquals(0, stats.foldedDraws)
    }

    private fun diagnose(draws: List<RenderDrawCommand>) = SceneDrawStats().also { diagnoseInstanceFolds(draws, plan, it) }

    private fun draw() = RenderDrawCommand(textured, material)

    /** [count] copies of one mesh, placed apart, each passed through [shape]; the mesh stays the first copy's. */
    private fun copies(count: Int, shape: (RenderDrawCommand) -> RenderDrawCommand = { it }): List<RenderDrawCommand> {
        val shaped = List(count) { shape(draw().copy(model = Mat4().translate(it.toFloat(), 0f, 0f))) }
        return shaped.map { it.copy(mesh = shaped[0].mesh) }
    }

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
