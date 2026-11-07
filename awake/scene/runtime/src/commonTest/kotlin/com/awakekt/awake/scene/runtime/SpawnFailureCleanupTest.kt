/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneComponentResolver
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneCustomComponent
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.mesh.MeshRendererBinding
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import kotlinx.serialization.json.JsonNull
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A spawn whose assets fail to resolve leaves the world as it found it, and still reports why (#1410). */
class SpawnFailureCleanupTest {
    init {
        SceneComponentRegistry.registerGlobal(MeshRendererBinding)
    }

    private val library = SceneAssetLibrary(
        meshFactories = mapOf(CUBE to { FakeMesh }),
        materialFactories = mapOf(LIT to { FakeMaterial }),
        rendererFactories = emptyMap(),
    )

    private val runtime = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = {},
            renderableFactory = { request -> library.resolve(this, request) },
            assetLibraryFactory = { library },
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        ),
    ).apply { initialize(NoServices) }

    @Test
    fun aSpawnWhoseMaterialCannotResolveLeavesNoEntityBehind() {
        val world = runtime.world
        world.add(world.create(), Name("Already here"))
        val named = world.query(Name::class).toSet()
        val placed = world.query(Transform::class).toSet()
        val alive = world.aliveCount()

        val failure = assertFailsWith<IllegalStateException> { runtime.spawn(PREFAB_COPY) }

        assertTrue(MISSING in failure.message.orEmpty(), "the caller still learns which material failed: ${failure.message}")
        assertEquals(named, world.query(Name::class).toSet(), "no named entity from the prefab remains")
        assertEquals(placed, world.query(Transform::class).toSet(), "no placed entity from the prefab remains")
        assertEquals(alive, world.aliveCount(), "the entity count is unchanged")
    }

    /** What the root's renderer took before its child failed is given back, as despawning would. */
    @Test
    fun aFailedSpawnReleasesWhatItsRenderersHeld() {
        assertFailsWith<IllegalStateException> { runtime.spawn(PREFAB_COPY) }

        assertEquals(0, library.meshHolderCount(CUBE))
        assertEquals(0, library.materialHolderCount(LIT))
    }

    /** A node that fails as it is instantiated, before any renderer resolves, leaves nothing behind either. */
    @Test
    fun aSpawnWhoseComponentFailsToBindLeavesNoEntityBehind() {
        val world = runtime.world
        val named = world.query(Name::class).toSet()
        val alive = world.aliveCount()
        val broken = PREFAB_COPY.copy(
            children = listOf(SceneNode(name = "Arrow", components = listOf(SceneCustomComponent(EXPLODES, JsonNull)))),
        )

        val failure = assertFailsWith<IllegalStateException> { runtime.spawn(broken, SceneComponentRegistry(resolvers = listOf(Explodes))) }

        assertEquals(EXPLODES, failure.message, "the binding's own failure reaches the caller")
        assertEquals(named, world.query(Name::class).toSet(), "neither the root nor the child that failed remains")
        assertEquals(alive, world.aliveCount(), "the entity count is unchanged")
        assertEquals(0, library.meshHolderCount(CUBE), "nothing was resolved for it")
    }

    /** The positive control: the same tree with every material resolvable spawns whole, and despawns clean. */
    @Test
    fun theSameTreeWithEveryAssetResolvableSpawns() {
        val alive = runtime.world.aliveCount()

        val spawned = runtime.spawn(PREFAB_COPY.copy(children = listOf(SceneNode(name = "Arrow", components = listOf(renderer(LIT))))))

        assertEquals(alive + 2, runtime.world.aliveCount())
        assertEquals(2, library.meshHolderCount(CUBE))
        spawned.despawn()
        assertEquals(alive, runtime.world.aliveCount())
    }

    // The live entities among the first ids: World keeps no count of its own.
    private fun World.aliveCount(): Int = (0 until ID_SCAN).count { entityById(it) != null }

    private object FakeMesh : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    /** A component binding that fails as its node is instantiated. */
    private object Explodes : SceneComponentResolver {
        override fun canResolve(component: SceneComponent) = component is SceneCustomComponent && component.type == EXPLODES

        override fun attach(world: World, entity: Entity, component: SceneComponent, context: SceneResolutionContext) = error(EXPLODES)
    }

    private object NoServices : AppServiceLookup {
        override fun <T : Any> service(type: KClass<T>): T? = null
    }

    private companion object {
        const val CUBE = "cube"
        const val LIT = "lit"
        const val MISSING = "gltf-material:assets/effects/effect-mesh.glb"
        const val ID_SCAN = 1024
        const val EXPLODES = "explodes"

        fun renderer(material: String) = SceneMeshRenderer(mesh = CUBE, material = material)

        /** A prefab's copy: a root that resolves, then a child whose material nothing can resolve. */
        val PREFAB_COPY = SceneNode(
            name = "Volley (copy)",
            components = listOf(renderer(LIT)),
            children = listOf(SceneNode(name = "Arrow", components = listOf(renderer(MISSING)))),
        )
    }
}
