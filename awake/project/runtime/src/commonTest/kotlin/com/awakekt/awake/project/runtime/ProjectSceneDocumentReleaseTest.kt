/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectSceneDocumentReleaseTest {

    @Test
    fun runProjectReleasesSceneDocumentAstAfterInstantiationAndSwitchWhilePreservingNameAndTerrain() = runTest {
        val project = loadProject(projectFiles(), physicsWorld = ::createJoltPhysicsWorld)
        assertEquals("harbor", project.scene.name)
        assertTrue(project.scene.nodes.isNotEmpty(), "loaded project holds nodes prior to instantiation")

        project.sceneLoads = Dispatchers.Unconfined
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()

        // Document AST is released right after instantiation, while header metadata & live ECS remain intact.
        assertEquals("harbor", project.scene.name, "scene name must survive AST release")
        assertEquals(1, project.scene.version)
        assertTrue(project.scene.nodes.isEmpty(), "runProject must drop decoded scene nodes after instantiation")
        assertTrue(project.scene.extensions.isEmpty())

        var terrainHeight: Float? = null
        runtime.world.queryEach<TerrainComponent> { _, terrain ->
            terrainHeight = terrain.heightmap.heightAtWorld(0f, 0f)
        }
        assertEquals(TERRAIN_HEIGHT, assertNotNull(terrainHeight), 0.01f, "ECS terrain heightmap must stay live")

        // Switch scenes and confirm the newly instantiated scene also releases its AST while updating name.
        val switcher = game.requireService<SceneSwitch>()
        switcher.request(SECOND_SCENE_PATH)
        repeat(SWITCH_FRAMES) { game.update(STEP, WIDTH, HEIGHT) }

        assertEquals("lighthouse", project.scene.name, "switched scene name must remain visible")
        assertTrue(project.scene.nodes.isEmpty(), "switched scene AST must also be released after instantiation")

        game.dispose()
        project.close()
        assertTrue(project.scene.nodes.isEmpty())
    }

    @Test
    fun runProjectWithRetainSceneDocumentKeepsDecodedNodes() = runTest {
        val project = loadProject(projectFiles(), physicsWorld = ::createJoltPhysicsWorld)
        val game = app { scene("play") { runProject(project, retainSceneDocument = true) } }
        game.ready(TestRenderer())

        assertEquals("harbor", project.scene.name)
        assertTrue(project.scene.nodes.isNotEmpty(), "retainSceneDocument = true must keep decoded nodes")

        game.dispose()
        project.close()
        assertTrue(project.scene.nodes.isEmpty(), "close() must still clear retained document")
    }

    @Test
    fun explicitReleaseSceneDocumentDropsNodesIsIdempotentAndGuardsNewSceneInstance() = runTest {
        val project = loadProject(projectFiles(), hasRenderer = false, physicsWorld = ::createJoltPhysicsWorld)
        assertEquals("harbor", project.scene.name)
        assertTrue(project.scene.nodes.isNotEmpty())

        project.releaseSceneDocument()
        project.releaseSceneDocument()

        assertEquals("harbor", project.scene.name)
        assertTrue(project.scene.nodes.isEmpty())
        assertFailsWith<IllegalStateException> { project.newSceneInstance() }

        project.close()
    }

    private fun projectFiles(): AssetSource {
        val samples = List(TERRAIN_SIDE * TERRAIN_SIDE) { TERRAIN_HEIGHT }.joinToString()
        val entryScene = """
            {
              "version": 1,
              "name": "harbor",
              "nodes": [
                { "name": "Camera", "components": [ { "component": "camera" } ] },
                { "name": "Ground", "components": [
                  { "component": "terrain", "width": $TERRAIN_SIDE, "depth": $TERRAIN_SIDE, "scaleX": 4.0, "scaleZ": 4.0, "samples": [$samples], "collider": true }
                ] }
              ]
            }
        """.trimIndent()
        val secondScene = """
            {
              "version": 1,
              "name": "lighthouse",
              "nodes": [
                { "name": "Camera", "components": [ { "component": "camera" } ] },
                { "name": "Beacon" }
              ]
            }
        """.trimIndent()
        val files = mapOf(
            PROJECT_MANIFEST to MANIFEST,
            ENTRY_SCENE_PATH to entryScene,
            SECOND_SCENE_PATH to secondScene,
        )
        return AssetSource { path -> runCatching { files.getValue(path.value).encodeToByteArray() } }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val WIDTH = 320f
        const val HEIGHT = 240f
        const val SWITCH_FRAMES = 3
        const val TERRAIN_SIDE = 9
        const val TERRAIN_HEIGHT = 3.5f
        const val ENTRY_SCENE_PATH = "scenes/main.scene.json"
        const val SECOND_SCENE_PATH = "scenes/lighthouse.scene.json"
        const val MANIFEST =
            """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"$ENTRY_SCENE_PATH"}"""
    }
}
