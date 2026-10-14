/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.ecs.World
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
import kotlin.test.assertFalse
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

    /** A host that plays a scene in a world of its own can spawn into that world, not the runtime's. */
    @Test
    fun spawningIntoASeparateWorldPutsItsEntitiesThere() {
        val target = World()
        val spawned = runtime.spawn(CART, target)

        assertEquals(target, spawned.world)
        spawned.entities.forEach { entity ->
            assertNotNull(target.get<MeshRenderer>(entity))
            assertFalse(runtime.world.isAlive(entity), "nothing was spawned into the runtime's own world")
        }
    }

    /**
     * The positive control for the world-aware spawn: before it existed, [attachRenderers] checked and
     * attached through the runtime's own world, so a renderer left off for a node spawned into another
     * world could never attach -- its entity is never alive there.
     */
    @Test
    fun aRendererLeftOffInASeparateWorldAttachesThere() {
        val target = World()
        val spawned = runtime.spawn(CART, target, SceneComponentRegistry()) { it.meshRenderer.mesh != HELD }
        val (cart, crate) = spawned.entities

        assertNotNull(target.get<MeshRenderer>(cart), "the others attach at once")
        assertNull(target.get<MeshRenderer>(crate))

        assertEquals(listOf(crate), spawned.attachRenderers { true })

        assertNotNull(target.get<MeshRenderer>(crate), "the renderer attaches into the node's own world")
        assertNull(runtime.world.get<MeshRenderer>(crate), "not into the runtime's own world")
    }

    @Test
    fun despawningASeparateWorldSpawnRemovesItsEntitiesThere() {
        val target = World()
        val spawned = runtime.spawn(CART, target)
        val entities = spawned.entities

        spawned.despawn()

        entities.forEach { assertFalse(target.isAlive(it)) }
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
