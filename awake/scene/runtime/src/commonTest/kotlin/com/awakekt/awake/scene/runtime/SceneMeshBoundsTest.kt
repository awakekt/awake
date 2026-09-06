/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneMeshRenderer
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.instantiate
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A loaded scene's meshes carry their own bounds, because three features depend on it and none of
 * them said so.
 *
 * `MeshBounds` is what frustum culling tests, what the spatial index indexes, and what the debug
 * overlay draws. Nothing attached it -- no scene document authored one, and the loader did not --
 * so on every authored scene all three were inert while looking implemented: culling with nothing
 * to cull reads exactly like culling that decided everything was visible.
 */
class SceneMeshBoundsTest {

    @Test
    fun aLoadedMeshRendererCarriesItsMeshesBounds() {
        val scene = document().instantiate()
        val bounds = Aabb(Vec3f(-1f, -2f, -3f), Vec3f(1f, 2f, 3f))

        scene.attachRenderableComponents { MeshRenderer(FakeMesh(bounds), FakeMaterial()) }

        val entity = scene.world.query(MeshRenderer::class).single()
        assertEquals(
            bounds,
            scene.world.get<MeshBounds>(entity)?.localBounds,
            "Without this the scene has no bounds at all, and culling, the spatial index and the " +
                "bounds overlay all quietly do nothing.",
        )
    }

    @Test
    fun aMeshWithNoBoundsAttachesNothing() {
        val scene = document().instantiate()

        scene.attachRenderableComponents { MeshRenderer(FakeMesh(localBounds = null), FakeMaterial()) }

        val entity = scene.world.query(MeshRenderer::class).single()
        assertNull(
            scene.world.get<MeshBounds>(entity),
            "A backend that recorded no bounds should leave the entity never-culled and always " +
                "drawn, which is what it did before bounds existed -- not culled against a box " +
                "nobody measured.",
        )
    }

    private fun document() = SceneDocument(
        name = "bounds",
        nodes = listOf(
            SceneNode(
                name = "prop",
                components = listOf(SceneMeshRenderer(mesh = "cube", material = "lit")),
            ),
        ),
    )

    private class FakeMesh(override val localBounds: Aabb?) : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private class FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
