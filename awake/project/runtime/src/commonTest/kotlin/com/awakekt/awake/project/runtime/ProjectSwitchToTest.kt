/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A host without a frame loop, such as a project check or a server, moves a loaded project to any of
 * its scenes with [switchTo]: with the project's capabilities, its physics world and its content, and
 * no rewritten manifest.
 */
class ProjectSwitchToTest {
    private val idle = { GameplayInput(Input().currentSnapshot, InputOwnership()) }

    @Test
    fun aHostWithoutAFrameLoopPlaysEachOfAProjectsScenes() = runTest {
        val beacons = LevelSignalCapability()
        val worlds = mutableListOf<CountedWorld>()
        val registry = SceneComponentRegistry.scoped()
        val project = loadProject(files(), registry, listOf(beacons)) { CountedWorld(createJoltPhysicsWorld()).also { worlds += it } }
        assertEquals("menu", project.scene.name)

        project.switchTo(LEVEL)

        assertEquals("level", project.scene.name)
        assertEquals(1, worlds.first().destroyed, "the menu's physics world is destroyed")
        assertEquals(2, worlds.size, "the level has a physics world of its own")
        assertEquals("ping", project.content[BEACON_SIGNAL], "the game's capability read the level's content")
        assertEquals(setOf(GLOW), project.content[CoreSceneContent.ShaderEffects]?.documents?.keys, "and so did Core's")
        val world = World()
        project.scene.instantiate(world, registry)
        project.sceneSystems(idle).use { systems -> repeat(STEPS) { systems.fixed.forEach { it.update(world, STEP) } } }
        val crateY = world.positionOf("Crate").y
        assertTrue(crateY in 0f..1f, "the crate falls onto the level's floor in the level's physics world; y = $crateY")
        assertEquals(listOf("ping"), beacons.heard, "the level's beacon system ran with the content its capability loaded")

        project.switchTo(MENU)

        assertEquals("menu", project.scene.name, "a project moves back to its entry scene too")
        assertEquals(1, worlds[1].destroyed, "the level's physics world is destroyed as the project leaves it")
        project.close()
        assertEquals(1, worlds.last().destroyed, "closing the project closes the scene playing now")
    }

    @Test
    fun aPathOutsideTheProjectOrAMissingOrBrokenSceneLeavesTheSceneThatPlays() = runTest {
        val worlds = mutableListOf<CountedWorld>()
        val project = loadProject(files(), SceneComponentRegistry.scoped(), listOf(LevelSignalCapability())) {
            CountedWorld(createJoltPhysicsWorld()).also { worlds += it }
        }

        for (path in listOf(OUTSIDE, "/$LEVEL", "scenes\\level.scene.json", "scenes/./level.scene.json", "", MISSING, BROKEN)) {
            val refused = assertFailsWith<IllegalArgumentException>("'$path' is refused") { project.switchTo(path) }
            assertTrue(path.isEmpty() || path in refused.message.orEmpty(), "the refusal names '$path': ${refused.message}")
            assertEquals("menu", project.scene.name, "after '$path', the menu still plays")
        }
        assertEquals(0, worlds.single().destroyed, "the menu's physics world is untouched")
        project.close()
    }

    @Test
    fun aClosedProjectOrOneThatRunProjectPlaysMovesNowhere() = runTest {
        val closed = loadProject(files(), SceneComponentRegistry.scoped(), listOf(LevelSignalCapability()), physicsWorld = ::createJoltPhysicsWorld)
        closed.close()
        assertFailsWith<IllegalStateException> { closed.switchTo(LEVEL) }

        val played = loadProject(files(), SceneComponentRegistry.scoped(), listOf(LevelSignalCapability()), physicsWorld = ::createJoltPhysicsWorld)
        played.switchTo(LEVEL)
        val game = app { scene("play") { runProject(played) } }
        game.ready(TestRenderer())
        game.update(STEP, WIDTH, HEIGHT)

        assertEquals("level", played.scene.name, "runProject starts at the scene the project was moved to")
        assertFailsWith<IllegalStateException> { played.switchTo(MENU) }
        played.close()
    }

    private fun World.positionOf(name: String) = buildList {
        queryEach(Name::class) { entity, value -> if (value.value == name) add(get<Transform>(entity)!!.position) }
    }.single()

    private fun files(): AssetSource {
        val texts = mapOf(
            PROJECT_MANIFEST to MANIFEST,
            MENU to MENU_SCENE,
            LEVEL to LEVEL_SCENE,
            BROKEN to BROKEN_SCENE,
            OUTSIDE to LEVEL_SCENE,
            GLOW to GLOW_DOCUMENT,
            SIGNAL to "ping",
        )
        return AssetSource { path -> runCatching { texts.getValue(path.value).encodeToByteArray() } }
    }

    /** A physics world that counts how often it is destroyed. */
    private class CountedWorld(private val inner: PhysicsWorld) : PhysicsWorld by inner {
        var destroyed = 0

        override fun destroy() {
            destroyed++
            inner.destroy()
        }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val STEPS = 120
        const val WIDTH = 800f
        const val HEIGHT = 600f

        const val MENU = "scenes/menu.scene.json"
        const val LEVEL = "scenes/level.scene.json"
        const val MISSING = "scenes/missing.scene.json"
        const val BROKEN = "scenes/broken.scene.json"
        const val OUTSIDE = "../level.scene.json"
        const val GLOW = "shaders/glow.shader.json"
        const val SIGNAL = "data/level.signal"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/menu.scene.json"}"""
        const val GLOW_DOCUMENT = """{"name":"Glow","surface":"background","fragment":{"color":{"op":"const","value":[1,0.8,0.2,1]}}}"""

        const val MENU_SCENE = """
{ "version": 1, "name": "menu", "nodes": [
  { "name": "Floor", "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 5.0, "y": 0.1, "z": 5.0 } } }
  ] }
] }
"""

        const val LEVEL_SCENE = """
{ "version": 1, "name": "level", "nodes": [
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": -0.5, "z": 0.0 } },
    "components": [ { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 10.0, "y": 0.5, "z": 10.0 } }, "motion": "STATIC" } ] },
  { "name": "Crate", "transform": { "position": { "x": 0.0, "y": 5.0, "z": 0.0 } },
    "components": [ { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 0.5, "y": 0.5, "z": 0.5 } }, "motion": "DYNAMIC" } ] },
  { "name": "Glow", "components": [ { "component": "shader_effect", "shader": "shaders/glow.shader.json" } ] },
  { "name": "Signal", "components": [ { "component": "test_level_signal", "signal": "data/level.signal" } ] }
] }
"""

        const val BROKEN_SCENE = """
{ "version": 1, "name": "broken", "nodes": [
  { "name": "Ghost", "components": [ { "component": "grappling_hook" } ] }
] }
"""

        /** What the beacon capability read from the file its scene's beacon names. */
        val BEACON_SIGNAL = SceneContentKey<String>("test beacon signal")
    }

    /** A game's own capability: a component, content it reads from the project, and a system that reads it. */
    private class LevelSignalCapability : SceneCapability {
        override val id = "com.example.harbor-town.level-signal"
        override val components = listOf(LevelSignalBinding)

        /** What each beacon system read from its content, once each. */
        val heard = mutableListOf<String>()

        override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
            val beacon = scene.nodes.firstNotNullOfOrNull { node -> node.components.filterIsInstance<SceneLevelSignal>().firstOrNull() } ?: return
            val signal = files.read(AssetPath(beacon.signal)).getOrElse { throw IllegalArgumentException("Can't read ${beacon.signal}", it) }
            content[BEACON_SIGNAL] = signal.decodeToString()
        }

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
            if (!scene.uses(SceneLevelSignal::class)) return
            plan.fixed("beacon") { services ->
                val signal = checkNotNull(services.content[BEACON_SIGNAL]) { "the beacon's content loaded" }
                object : System {
                    override fun update(world: World, delta: Float) {
                        if (heard.isEmpty()) heard += signal
                    }
                }
            }
        }
    }
}

/** A beacon in a test scene, naming the project file its capability reads. */
@Serializable
@SerialName("test_level_signal")
private data class SceneLevelSignal(val signal: String = "") : SceneComponent

/** The beacon on an entity. */
private class LevelSignal(val signal: String)

private object LevelSignalBinding : SceneComponentBinding<LevelSignal, SceneLevelSignal> {
    override val componentClass: KClass<LevelSignal> = LevelSignal::class
    override val schemaClass: KClass<SceneLevelSignal> = SceneLevelSignal::class
    override val serializer = SceneLevelSignal.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneLevelSignal, context: SceneResolutionContext) {
        world.add(entity, LevelSignal(component.signal))
    }

    override fun export(world: World, entity: Entity, component: LevelSignal): SceneLevelSignal = SceneLevelSignal(component.signal)
}
