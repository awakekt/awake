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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** One [InstanceBatcher] kept across frames, as a scene's planner keeps it. */
class InstanceBatcherTest {
    private val batcher = InstanceBatcher()
    private val canInstance = { _: VertexFormat, _: com.awakekt.awake.render.pipeline.CullMode -> true }

    @Test
    fun aSecondFrameGroupsItsOwnDrawsNotTheLastFrames() {
        val meshes = List(3) { mesh() }
        val material = material()
        batcher.batch(meshes.flatMap { mesh -> List(4) { draw(mesh, material, it) } }, canInstance)

        val second = batcher.batch(List(2) { draw(meshes[2], material, it) } + draw(meshes[0], material, 9), canInstance)

        assertEquals(listOf(1, 2), second.map { it.instanceModels?.size ?: 1 }.sorted())
        val models = second.single { it.instanceModels != null }.instanceModels.orEmpty()
        assertEquals(listOf(at(0f), at(1f)).map { it.data.toList() }, models.map { it.data.toList() })
    }

    @Test
    fun manyDistinctGroupsGrowTheTableAndStayApart() {
        val material = material()
        val meshes = List(600) { mesh() }

        val batched = batcher.batch(meshes.flatMap { mesh -> List(2) { draw(mesh, material, it) } }, canInstance)

        assertEquals(600, batched.size)
        assertEquals(meshes.toSet(), batched.map { it.mesh }.toSet())
    }

    @Test
    fun anInstancedDrawKeepsWhatCopyingTheRequestUsedToDrop() {
        val mesh = mesh()
        val material = material()
        val draws = List(3) { draw(mesh, material, it).apply { fadeOpacity = 0.5f } }

        val draw = batcher.batch(draws, canInstance).single()

        assertEquals(0.5f, draw.fadeOpacity, "a fading group fades as one draw")
        assertEquals(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(3f, 1f, 1f)), draw.worldBounds)
    }

    @Test
    fun oneUnboundedCopyMakesTheInstancedDrawUnbounded() {
        val mesh = mesh()
        val material = material()
        val draws = List(3) { draw(mesh, material, it) }.also { it[1].worldBounds = null }

        assertNull(batcher.batch(draws, canInstance).single().worldBounds)
    }

    private fun draw(mesh: Mesh, material: Material, x: Int) = RenderDrawCommand(
        mesh,
        material,
        model = at(x.toFloat()),
        worldBounds = Aabb(Vec3f(x - 1f, -1f, -1f), Vec3f(x + 1f, 1f, 1f)),
    )

    private fun at(x: Float) = Mat4().translate(x, 0f, 0f)

    private fun mesh() = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun material() = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
