/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A spawned node can leave a renderer off until what it draws is ready, and attach it then. */
class SpawnedNodeTest {
    private val built = mutableListOf<String>()
    private val runtime = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = {},
            renderableFactory = { request ->
                built += request.meshRenderer.mesh
                MeshRenderer(FakeMesh, FakeMaterial)
            },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        ),
    ).apply { initialize(NoServices) }

    @Test
    fun aRendererLeftOffAttachesWhenAsked() {
        val spawned = runtime.spawn(CART, SceneComponentRegistry()) { it.meshRenderer.mesh != HELD }
        val (cart, crate) = spawned.entities

        assertNotNull(runtime.world.get<MeshRenderer>(cart), "the others attach at once")
        assertNull(runtime.world.get<MeshRenderer>(crate))
        assertEquals(listOf(HELD), spawned.unattachedRenderables.map { it.meshRenderer.mesh })

        assertEquals(emptyList(), spawned.attachRenderers { false })
        assertEquals(listOf(crate), spawned.attachRenderers { true })

        assertNotNull(runtime.world.get<MeshRenderer>(crate))
        assertEquals(emptyList(), spawned.unattachedRenderables)
        assertEquals(listOf(CUBE, HELD), built, "each renderer is built once")
    }

    /** The positive control: the plain spawn attaches every renderer at once and leaves none waiting. */
    @Test
    fun aPlainSpawnAttachesEveryRenderer() {
        val spawned = runtime.spawn(CART)

        spawned.entities.forEach { assertNotNull(runtime.world.get<MeshRenderer>(it)) }
        assertEquals(emptyList(), spawned.unattachedRenderables)
    }

    @Test
    fun aDespawnedNodeAttachesNothing() {
        val spawned = runtime.spawn(CART, SceneComponentRegistry()) { false }

        spawned.despawn()

        assertEquals(emptyList(), spawned.attachRenderers { true })
        assertEquals(emptyList(), spawned.unattachedRenderables)
        assertEquals(emptyList(), built, "nothing was built for it")
    }

    @Test
    fun aRequestWhoseEntityIsGoneIsDropped() {
        val spawned = runtime.spawn(CART, SceneComponentRegistry()) { it.meshRenderer.mesh != HELD }
        val crate = spawned.entities[1]

        runtime.world.destroy(crate)

        assertEquals(emptyList(), spawned.attachRenderers { true })
        assertEquals(emptyList(), spawned.unattachedRenderables)
        assertEquals(listOf(CUBE), built)
    }

    private object FakeMesh : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private object NoServices : AppServiceLookup {
        override fun <T : Any> service(type: KClass<T>): T? = null
    }

    private companion object {
        const val CUBE = "cube"
        const val HELD = "models/crate.glb"
        val CART = SceneNode(
            name = "Cart",
            components = listOf(SceneMeshRenderer(mesh = CUBE, material = "lit")),
            children = listOf(SceneNode(name = "Crate", components = listOf(SceneMeshRenderer(mesh = HELD, material = "lit")))),
        )
    }
}
