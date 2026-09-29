/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.player

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectPlayerTest {
    @Test
    fun aTemplateProjectPlaysWithItsCameraFollowingThePlayer() = runTest {
        val project = loadPlayableProject(files(ARENA_PROJECT))
        val game = app { scene("play") { playProject(project) } }
        game.ready(TestRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        game.update(DELTA, WIDTH, HEIGHT)

        val world = runtime.world
        var player = assertNotNull(world.entityNamed("Player"))
        var cameras = 0
        world.queryEach(Camera::class) { entity, camera ->
            cameras++
            assertTrue(camera.isPrimary, "the scene's only camera must be the one rendered from")
            assertTrue(world.has(entity, ActiveCamera::class))
            assertEquals(player, world.get<CameraRig>(entity)?.targetEntity, "the camera must follow the player")
        }
        assertEquals(1, cameras)
        var drawn = 0
        world.queryEach(MeshRenderer::class) { _, _ -> drawn++ }
        assertEquals(2, drawn, "the built-in cube and checkered floor must resolve")
    }

    @Test
    fun heldKeysMoveThePlayerAndSpaceJumps() = runTest {
        val project = loadPlayableProject(files(ARENA_PROJECT))
        val game = app { scene("play") { playProject(project) } }
        game.ready(TestRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val input = game.requireService<Input>()
        game.update(DELTA, WIDTH, HEIGHT)
        val player = assertNotNull(runtime.world.entityNamed("Player"))
        val position = runtime.world.get<Transform>(player)!!.position
        val start = position.copy()

        input.setKeyDown(Key.W, true)
        input.setKeyDown(Key.Space, true)
        input.updateSnapshot()
        var peak = position.y
        repeat(FRAMES) {
            game.update(DELTA, WIDTH, HEIGHT)
            peak = maxOf(peak, position.y)
        }

        assertTrue(position.z < start.z - 1f, "W must walk away from the camera (-Z); z went ${start.z} -> ${position.z}")
        assertTrue(peak > start.y + 0.5f, "Space must jump; peak $peak from ${start.y}")
        assertEquals(6f, runtime.world.get<MovementControl>(player)?.speed)
    }

    @Test
    fun aManifestWithoutItsEntrySceneIsRefused() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            loadPlayableProject(files(mapOf("awake.project.json" to MANIFEST)))
        }
        assertTrue("scenes/main.scene.json" in error.message.orEmpty(), error.message)
    }

    /** Records nothing; the scene render system only needs a draw preparer to exist. */
    private class TestRenderer : NoopRenderer(), GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private fun files(entries: Map<String, String>) = AssetSource { path ->
        entries[path.value]?.let { Result.success(it.encodeToByteArray()) }
            ?: Result.failure(NoSuchElementException(path.value))
    }

    private fun com.awakekt.awake.ecs.World.entityNamed(name: String): com.awakekt.awake.ecs.Entity? {
        var found: com.awakekt.awake.ecs.Entity? = null
        queryEach(com.awakekt.awake.scene.core.Name::class) { entity, value -> if (value.value == name) found = entity }
        return found
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val FRAMES = 30

        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        // The shape Awake Studio's third-person template writes, trimmed to what the player reads.
        const val SCENE = """
{
  "version": 1,
  "name": "arena",
  "nodes": [
    { "name": "Camera", "transform": { "position": { "x": 0.0, "y": 4.0, "z": 8.0 } },
      "components": [ { "component": "camera", "primary": false } ] },
    { "name": "Player", "transform": { "position": { "x": 0.0, "y": 0.5, "z": 0.0 } },
      "components": [
        { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" },
        { "component": "movement_control", "speed": 6.0 }
      ] },
    { "name": "Floor",
      "components": [ { "component": "meshRenderer", "mesh": "checkered-floor", "material": "lit-shadow", "cullMode": "None" } ] }
  ]
}
"""
        val ARENA_PROJECT = mapOf("awake.project.json" to MANIFEST, "scenes/main.scene.json" to SCENE)
    }
}
