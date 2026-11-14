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
import com.awakekt.awake.render.pipeline.CullMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Folded groups keep their place in the draw list from frame to frame (#1555): a backend keys its
 * per-draw buffers by position, so a group that moved re-uploaded every draw after it.
 *
 * Before, groups followed their first copies this frame: culling the first copy of the first
 * mesh's group moved that group behind the others, and every other group forward.
 */
class InstanceGroupOrderTest {
    private val batcher = InstanceBatcher()
    private val canInstance = { _: VertexFormat, _: CullMode -> true }
    private val material = material()
    private val meshes = List(3) { mesh() }

    @Test
    fun removingAnyCopyOfOneMeshMovesNoOtherGroup() {
        val first = positions(batcher.batch(interleaved(copies = 3), canInstance))

        for (removed in 0 until 3) {
            val draws = interleaved(copies = 3).filterIndexed { index, _ -> index != removed * meshes.size }
            assertEquals(first, positions(batcher.batch(draws, canInstance)), "removing copy $removed of the first mesh")
        }
    }

    @Test
    fun removingADrawThatNeverFoldsMovesNoGroup() {
        // A character's skinned or blended draw never folds; culled in or out, it must not move the scenery.
        val character = draw(mesh(), 0).apply { transparent = true }
        val with = positions(batcher.batch(listOf(character) + interleaved(copies = 2), canInstance))

        assertEquals(with, positions(batcher.batch(interleaved(copies = 2), canInstance)), "the character culled")
        assertEquals(with, positions(batcher.batch(interleaved(copies = 2) + character, canInstance)), "and back")
    }

    @Test
    fun aGroupThatDisappearsFreesItsPlaceAndReturnsLast() {
        batcher.batch(interleaved(copies = 2), canInstance)
        val without = interleaved(copies = 2).filter { it.mesh !== meshes[0] }

        assertEquals(listOf(meshes[1], meshes[2]), batcher.batch(without, canInstance).map { it.mesh })
        assertEquals(2, batcher.order.size, "the gone group's place is freed")
        assertEquals(listOf(meshes[1], meshes[2], meshes[0]), batcher.batch(interleaved(copies = 2), canInstance).map { it.mesh })
    }

    @Test
    fun placesNeverOutnumberTheLastFramesGroupsOverALongSession() {
        repeat(SESSION_FRAMES) { frame ->
            // A fresh set of meshes every frame, as a streamed world's cells come and go.
            val streamed = List(GROUPS) { mesh() }
            batcher.batch(streamed.flatMap { mesh -> List(2) { draw(mesh, it) } }, canInstance)
            assertTrue(batcher.order.size <= GROUPS, "frame $frame holds ${batcher.order.size} places")
        }
    }

    @Test
    fun aNewGroupTakesThePlaceAfterEveryOther() {
        batcher.batch(interleaved(copies = 2).filter { it.mesh !== meshes[1] }, canInstance)

        assertEquals(listOf(meshes[0], meshes[2], meshes[1]), batcher.batch(interleaved(copies = 2), canInstance).map { it.mesh })
    }

    /** Each mesh's copies in turn: the first mesh's first copy leads the frame. */
    private fun interleaved(copies: Int) = (0 until copies).flatMap { copy -> meshes.map { draw(it, copy) } }

    /** Each mesh's index in the batched list. */
    private fun positions(batched: List<RenderDrawCommand>) = meshes.map { mesh -> batched.indexOfFirst { it.mesh === mesh } }

    private fun draw(mesh: Mesh, x: Int) = RenderDrawCommand(mesh, material, model = Mat4().translate(x.toFloat(), 0f, 0f))

    private fun mesh() = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun material() = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val SESSION_FRAMES = 1_000
        const val GROUPS = 50
    }
}
