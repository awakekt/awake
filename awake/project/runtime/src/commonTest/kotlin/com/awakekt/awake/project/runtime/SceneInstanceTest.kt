/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * One loaded project backs many independent instances of its scene, as a server running a copy of a
 * map per party does: each has its own world, systems and physics world, and shares the data the
 * project loaded once.
 */
class SceneInstanceTest {
    private val idle = { GameplayInput(Input().currentSnapshot, InputOwnership()) }
    private val worlds = mutableListOf<CountedWorld>()

    private suspend fun load() = loadProject(files(), hasRenderer = false) {
        CountedWorld(createJoltPhysicsWorld()).also { worlds += it }
    }

    @Test
    fun twoInstancesStepIndependentlyAndABodyMovedInOneStaysPutInTheOther() = runTest {
        val project = load()
        val first = project.newSceneInstance()
        val second = project.newSceneInstance()
        val firstSystems = first.sceneSystems(idle, coroutineScope = backgroundScope)
        val secondSystems = second.sceneSystems(idle, coroutineScope = backgroundScope)

        firstSystems.step(first.world, STEPS)

        val fallen = first.world.crateY()
        assertTrue(fallen in 0f..1f, "the first instance's crate falls onto its floor; y = $fallen")
        assertEquals(START_Y, second.world.crateY(), "the second instance's crate hasn't moved: nothing stepped it")

        secondSystems.step(second.world, FEW_STEPS)

        val falling = second.world.crateY()
        assertTrue(falling in 1f..<START_Y, "the second instance's crate falls on its own steps; y = $falling")
        assertEquals(fallen, first.world.crateY(), "stepping the second moves nothing in the first")
        assertEquals(3, worlds.size, "the project's scene and each instance have a physics world of their own")
        first.close()
        second.close()
        project.close()
    }

    @Test
    fun closingAnInstanceFreesOnlyItsPhysicsWorld() = runTest {
        val project = load()
        val content = project.content
        val closing = project.newSceneInstance()
        val staying = project.newSceneInstance()
        closing.sceneSystems(idle, coroutineScope = backgroundScope).step(closing.world, FEW_STEPS)
        val stayingSystems = staying.sceneSystems(idle, coroutineScope = backgroundScope)

        closing.close()

        assertEquals(listOf(0, 1, 0), worlds.map { it.destroyed }, "only the closed instance's physics world is destroyed")
        assertEquals(0, closing.world.entityCount, "its entities are gone with it")
        assertTrue(closing.isClosed)
        assertFailsWith<IllegalStateException> { closing.sceneSystems(idle, coroutineScope = backgroundScope) }
        stayingSystems.step(staying.world, STEPS)
        assertTrue(staying.world.crateY() in 0f..1f, "the other instance plays on; y = ${staying.world.crateY()}")
        assertSame(content, project.content, "the shared content is untouched")
        assertSame(project.scene, staying.scene, "and so is the shared document")
        closing.close()
        assertEquals(1, worlds[1].destroyed, "closing twice is harmless")
        staying.close()
        project.close()
    }

    @Test
    fun anInstanceOutlivesItsProjectAndAClosedProjectMakesNoMore() = runTest {
        val project = load()
        val instance = project.newSceneInstance()

        project.close()

        assertEquals(listOf(1, 0), worlds.map { it.destroyed }, "the project destroys its own scene's world, not the instance's")
        instance.sceneSystems(idle, coroutineScope = backgroundScope).step(instance.world, STEPS)
        assertTrue(instance.world.crateY() in 0f..1f, "the instance plays on; y = ${instance.world.crateY()}")
        assertFailsWith<IllegalStateException> { project.newSceneInstance() }
        instance.close()
    }

    @Test
    fun aSceneWithoutBodiesMakesInstancesWithoutAPhysicsWorld() = runTest {
        val project = loadProject(files(scene = STILL_SCENE), hasRenderer = false) { error("no bodies, so no physics world") }

        val instance = project.newSceneInstance()

        assertTrue(!instance.hasPhysics)
        assertEquals(1, instance.world.entityCount)
        instance.close()
        project.close()
    }

    private fun SceneSystemSet.step(world: World, steps: Int) = repeat(steps) { fixed.forEach { it.update(world, STEP) } }

    private val World.entityCount: Int get() = query(Name::class).size

    private fun World.crateY(): Float = buildList {
        queryEach(Name::class) { entity, name -> if (name.value == "Crate") add(get<Transform>(entity)!!.position.y) }
    }.single()

    private fun files(scene: String = SCENE): AssetSource {
        val sources = mapOf(PROJECT_MANIFEST to MANIFEST, "scenes/main.scene.json" to scene)
        return AssetSource { path -> runCatching { sources.getValue(path.value).encodeToByteArray() } }
    }

    private class CountedWorld(private val inner: PhysicsWorld) : PhysicsWorld by inner {
        var destroyed = 0

        override fun destroy() {
            destroyed++
            inner.destroy()
        }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val STEPS = 120
        const val FEW_STEPS = 20
        const val START_Y = 5f
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": -0.5, "z": 0.0 } },
    "components": [ { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 10.0, "y": 0.5, "z": 10.0 } }, "motion": "STATIC" } ] },
  { "name": "Crate", "transform": { "position": { "x": 0.0, "y": 5.0, "z": 0.0 } },
    "components": [ { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 0.5, "y": 0.5, "z": 0.5 } }, "motion": "DYNAMIC" } ] }
] }
"""
        const val STILL_SCENE = """
{ "version": 1, "name": "quay", "nodes": [ { "name": "Crate", "transform": { "position": { "x": 0.0, "y": 5.0, "z": 0.0 } } } ] }
"""
    }
}
