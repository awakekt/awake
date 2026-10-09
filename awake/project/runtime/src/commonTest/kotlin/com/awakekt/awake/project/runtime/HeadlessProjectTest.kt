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
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.particles.ParticleContentSystem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A project runs with no renderer, as a game server runs it: it simulates, and draws nothing. */
class HeadlessProjectTest {
    private val idle = { GameplayInput(Input().currentSnapshot, InputOwnership()) }

    @Test
    fun aHeadlessProjectSimulatesItsPhysics() = runTest {
        val project = loadProject(files(SCENE), physicsWorld = ::createJoltPhysicsWorld)
        val world = World()
        project.scene.instantiate(world = world)
        project.sceneSystems(idle).use { systems ->
            repeat(STEPS) { systems.fixed.forEach { it.update(world, STEP) } }
        }
        val crateY = world.positionOf("Crate").y
        project.close()

        assertTrue(crateY < 1f, "the crate must fall from y = 5 onto the floor; y = $crateY")
        assertTrue(crateY > 0f, "the floor must stop it; y = $crateY")
    }

    @Test
    fun aHeadlessHostLeavesOutTheSystemsThatDraw() = runTest {
        val project = loadProject(files(SCENE), physicsWorld = ::createJoltPhysicsWorld)

        val headless = project.sceneSystems(idle)
        val drawn = project.sceneSystems(idle, NoopRenderer())

        assertFalse(headless.frame.any { it is ParticleContentSystem }, "a server has no particle sprites to make")
        assertTrue(drawn.frame.any { it is ParticleContentSystem }, "the positive control: a drawn host keeps them")
        headless.close()
        drawn.close()
        project.close()
    }

    @Test
    fun aHeadlessHostHasNoRenderer() {
        val services = SceneHostServices.headless(idle)

        assertFalse(services.hasRenderer)
        assertFailsWith<IllegalStateException> { services.renderer }
    }

    private fun World.positionOf(name: String) = buildList {
        queryEach(Name::class) { entity, value -> if (value.value == name) add(get<Transform>(entity)!!.position) }
    }.single()

    private fun files(scene: String): AssetSource {
        val sources = mapOf(PROJECT_MANIFEST to MANIFEST, "scenes/main.scene.json" to scene)
        return AssetSource { path -> runCatching { sources.getValue(path.value).encodeToByteArray() } }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val STEPS = 120
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": -0.5, "z": 0.0 } },
    "components": [ { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 10.0, "y": 0.5, "z": 10.0 } }, "motion": "STATIC" } ] },
  { "name": "Crate", "transform": { "position": { "x": 0.0, "y": 5.0, "z": 0.0 } },
    "components": [ { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 0.5, "y": 0.5, "z": 0.5 } }, "motion": "DYNAMIC" } ] },
  { "name": "Sparks", "components": [ { "component": "particle_emitter", "texture": "spark.png" } ] }
] }
"""
    }
}
