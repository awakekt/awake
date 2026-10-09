/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.movement.SceneMovementControl
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneSerializers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Two projects that use one component name for different components load in one process. */
class ScopedComponentRegistryTest {
    @Test
    fun twoProjectsWithTheSameComponentNameEachDecodeTheirOwn() = runTest {
        val docks = loadProject(files(DOCK_SCENE), SceneComponentRegistry.scoped(), listOf(DockCapability))
        val cranes = loadProject(files(CRANE_SCENE), SceneComponentRegistry.scoped(), listOf(CraneCapability))

        assertEquals(listOf(DockSpawner(boats = 3), SceneMovementControl()), docks.scene.components())
        assertEquals(listOf(CraneSpawner(reach = 2.5f)), cranes.scene.components())
        assertTrue(
            SceneSerializers.registeredSerializers().none { it.descriptor.serialName == SPAWNER },
            "a scoped load must not register its components globally",
        )
    }

    @Test
    fun aScopedProjectAttachesItsComponentsWithItsOwnRegistry() = runTest {
        val registry = SceneComponentRegistry.scoped()
        val project = loadProject(files(DOCK_SCENE), registry, listOf(DockCapability))
        val world = World()

        SceneLoader.instantiate(project.scene, world, registry)

        var boats = 0
        world.queryEach(Dock::class) { _, dock -> boats += dock.boats }
        assertEquals(3, boats)
    }

    @Test
    fun aScopeDecodesOnlyTheComponentsItHolds() = runTest {
        val failure = assertFailsWith<IllegalArgumentException> { loadProject(files(DOCK_SCENE), SceneComponentRegistry.scoped()) }
        assertTrue(SPAWNER in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun anEditorDecodesWithTheRegistryItPreparedForTheProject() {
        val registry = SceneComponentRegistry.scoped().registerProjectComponents(listOf(CraneCapability))

        val scene = SceneLoader.decode(CRANE_SCENE, registry.sceneJson())

        assertEquals(listOf(CraneSpawner(reach = 2.5f)), scene.components())
    }

    private fun SceneDocument.components(): List<SceneComponent> = nodes.flatMap { it.components }

    private fun files(scene: String) = AssetSource { path ->
        runCatching { mapOf("awake.project.json" to MANIFEST, "scenes/main.scene.json" to scene).getValue(path.value).encodeToByteArray() }
    }

    @Serializable
    @SerialName(SPAWNER)
    data class DockSpawner(val boats: Int) : SceneComponent

    @Serializable
    @SerialName(SPAWNER)
    data class CraneSpawner(val reach: Float) : SceneComponent

    data class Dock(val boats: Int)

    data class Crane(val reach: Float)

    private object DockBinding : SceneComponentBinding<Dock, DockSpawner> {
        override val componentClass = Dock::class
        override val schemaClass = DockSpawner::class
        override val serializer = DockSpawner.serializer()
        override fun attachTyped(world: World, entity: Entity, component: DockSpawner, context: SceneResolutionContext) {
            world.add(entity, Dock(component.boats))
        }
        override fun export(world: World, entity: Entity, component: Dock) = DockSpawner(component.boats)
    }

    private object CraneBinding : SceneComponentBinding<Crane, CraneSpawner> {
        override val componentClass = Crane::class
        override val schemaClass = CraneSpawner::class
        override val serializer = CraneSpawner.serializer()
        override fun attachTyped(world: World, entity: Entity, component: CraneSpawner, context: SceneResolutionContext) {
            world.add(entity, Crane(component.reach))
        }
        override fun export(world: World, entity: Entity, component: Crane) = CraneSpawner(component.reach)
    }

    private object DockCapability : SceneCapability {
        override val id = "com.example.docks"
        override val components = listOf(DockBinding)
        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) = Unit
    }

    private object CraneCapability : SceneCapability {
        override val id = "com.example.cranes"
        override val components = listOf(CraneBinding)
        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) = Unit
    }

    private companion object {
        const val SPAWNER = "harbor_spawner"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val DOCK_SCENE = """
{ "version": 1, "name": "docks", "nodes": [
  { "name": "Pier", "components": [ { "component": "harbor_spawner", "boats": 3 }, { "component": "movement_control" } ] } ] }
"""
        const val CRANE_SCENE = """
{ "version": 1, "name": "cranes", "nodes": [
  { "name": "Yard", "components": [ { "component": "harbor_spawner", "reach": 2.5 } ] } ] }
"""
    }
}
