/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.kit.terrainlayers.TERRAIN_LAYERS_PROVIDER
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.terrain.TerrainSurface
import com.awakekt.awake.terrain.TerrainSurfaceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

/** A project's `terrain` draws when it is played on a renderer, with nothing wired by the host. */
class ProjectTerrainTest {
    private val input = Input()

    private fun services(renderer: HostRenderer, content: SceneContent) = SceneHostServices(
        input = { GameplayInput(input.currentSnapshot, InputOwnership()) },
        renderer = renderer,
        content = content,
    )

    @Test
    fun aPlainTerrainDrawsAndIsDetachedWhenTheSceneCloses() = runTest {
        val project = loadProject(files(PLAIN_TERRAIN))
        val renderer = HostRenderer()
        val systems = sceneSystemsFor(project.scene, services(renderer, project.content))
        val world = World().also { SceneLoader.instantiate(project.scene, it) }

        repeat(2) { systems.frame.forEach { system -> system.update(world, DELTA) } }
        assertEquals(1, renderer.attached, "drawn with the built-in terrain shading")

        systems.close()
        assertEquals(1, renderer.detached)
    }

    @Test
    fun aProjectsContentHoldsTheLayeredTerrainProvider() = runTest {
        val project = loadProject(files(PLAIN_TERRAIN))

        assertEquals(setOf(TERRAIN_LAYERS_PROVIDER), project.content[CoreSceneContent.TerrainSurfaces]?.keys)
    }

    /** A host running the scene's systems itself supplies a provider of its own as content. */
    @Test
    fun aSurfaceIsDrawnByTheProviderTheHostSupplies() = runTest {
        installProjectComponents()
        val scene = SceneLoader.decode(SURFACED_TERRAIN)
        var resolved = 0
        val provider = TerrainSurfaceProvider {
            resolved++
            TerrainSurface(PackShaderSets.Terrain)
        }
        val renderer = HostRenderer()
        val content = SceneContent.build { this[CoreSceneContent.TerrainSurfaces] = mapOf("test.surface" to provider) }
        val systems = sceneSystemsFor(scene, services(renderer, content))
        val world = World().also { SceneLoader.instantiate(scene, it) }

        repeat(ATTEMPTS) {
            systems.frame.forEach { system -> system.update(world, DELTA) }
            if (renderer.attached == 1) {
                assertEquals(1, resolved)
                systems.close()
                return@runTest
            }
            withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
        }
        fail("The surfaced terrain was not drawn")
    }

    @Test
    fun aHeadlessHostDrawsNoTerrain() {
        installProjectComponents()

        val systems = sceneSystemsFor(
            SceneLoader.decode(PLAIN_TERRAIN),
            SceneHostServices.headless(input = { GameplayInput(input.currentSnapshot, InputOwnership()) }),
        )

        assertFalse(systems.frame.any { it is TerrainDrawingSystem })
    }

    private class HostRenderer : NoopRenderer(), ContentFeatureHost {
        var attached = 0
        var detached = 0

        override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature {
            attached++
            return AttachedContentFeature { detached++ }
        }
    }

    private fun files(scene: String) = AssetSource { path ->
        runCatching { mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to scene).getValue(path.value).encodeToByteArray() }
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val ATTEMPTS = 100
        const val WAIT_MILLIS = 10L
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        const val PLAIN_TERRAIN = """
{ "version": 1, "name": "ground", "nodes": [
  { "name": "Ground", "components": [
    { "component": "terrain", "width": 2, "depth": 2, "samples": [0.0, 0.0, 0.0, 0.0] }
  ] }
] }
"""

        const val SURFACED_TERRAIN = """
{ "version": 1, "name": "ground", "nodes": [
  { "name": "Ground", "components": [
    { "component": "terrain", "width": 2, "depth": 2, "samples": [0.0, 0.0, 0.0, 0.0],
      "surface": { "provider": "test.surface" } }
  ] }
] }
"""
    }
}
