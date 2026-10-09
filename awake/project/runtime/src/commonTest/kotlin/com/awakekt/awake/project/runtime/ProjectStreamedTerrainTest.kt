/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.RawHeightmapCodec
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.terrain.TerrainPageAssets
import com.awakekt.awake.terrain.TerrainPageIndex
import com.awakekt.awake.terrain.TerrainPageIndexCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** A project scene with a `paged_terrain` plays through [loadProject] with no Kotlin of its own. */
class ProjectStreamedTerrainTest {
    private val input = Input()

    @Test
    fun cellsLoadAndUnloadAroundTheCameraAsItMoves() = runTest {
        val project = loadProject(files(scene(collider = false)))
        val terrain = assertNotNull(project.content[StreamedTerrainCapability.Terrain]).terrain
        val renderer = HostRenderer()
        val systems = sceneSystemsFor(project.scene, services(renderer, project))
        val world = World().also { SceneLoader.instantiate(project.scene, it) }
        val camera = camera(world, x = 2f)

        systems.stepUntil(world) { terrain.residentCoords == setOf(TerrainPageCoord(0, 0)) }
        assertEquals(1, renderer.attached)

        camera.lens.eye.x = 6f
        systems.stepUntil(world) { terrain.residentCoords == setOf(TerrainPageCoord(1, 0)) }

        systems.close()
        assertEquals(1, renderer.detached)
        assertTrue(terrain.residentCoords.isEmpty())
    }

    @Test
    fun nearbyCellsCollideAndAnUnloadedCellLeavesNoBody() = runTest {
        val project = loadProject(files(scene(collider = true))) { createJoltPhysicsWorld() }
        try {
            val physics = assertNotNull(project.physics)
            val systems = sceneSystemsFor(project.scene, services(NoopRenderer(), project))
            val world = World().also { SceneLoader.instantiate(project.scene, it) }
            val camera = camera(world, x = 2f)

            systems.stepUntil(world) { physics.raycast(Vec3f(2f, 20f, 2f), DOWN, 40f) != null }

            camera.lens.eye.x = 10f
            systems.stepUntil(world) { physics.raycast(Vec3f(10f, 20f, 2f), DOWN, 40f) != null }
            assertNull(physics.raycast(Vec3f(2f, 20f, 2f), DOWN, 40f), "the unloaded cell's body must be destroyed")
            var bodies = 0
            world.queryEach(PhysicsBody::class) { _, body -> if (body.handle != null) bodies++ }
            assertEquals(1, bodies)
            systems.close()
        } finally {
            project.close()
        }
    }

    @Test
    fun aCollidingPagedTerrainNeedsAPhysicsWorld() = runTest {
        assertFailsWith<IllegalArgumentException> { loadProject(files(scene(collider = true))) }
    }

    @Test
    fun aSceneStreamsOnePagedTerrain() = runTest {
        val twice = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "A", "components": [ { "component": "paged_terrain", "index": "world/index.json" } ] },
  { "name": "B", "components": [ { "component": "paged_terrain", "index": "world/index.json" } ] } ] }
"""
        assertFailsWith<IllegalArgumentException> { loadProject(files(twice)) }
    }

    private fun services(renderer: NoopRenderer, project: LoadedProject) = SceneHostServices(
        input = { GameplayInput(input.currentSnapshot, InputOwnership()) },
        renderer = renderer,
        physics = project.physics,
        content = project.content,
    )

    private fun camera(world: World, x: Float): Camera {
        val camera = Camera(lens = Lens(eye = Vec3f(x, 10f, 2f), center = Vec3f(x, 0f, 2.5f), fovYRadians = 1f, near = 0.1f, far = 200f))
        world.add(world.create(), camera)
        return camera
    }

    /** Steps the fixed systems until [done], waiting on the cell reads that finish off the test thread. */
    private suspend fun SceneSystemSet.stepUntil(world: World, done: () -> Boolean) {
        repeat(ATTEMPTS) {
            fixed.forEach { it.update(world, STEP) }
            if (done()) return
            withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
        }
        fail("The streamed terrain did not reach the expected state")
    }

    private class HostRenderer : NoopRenderer(), ContentFeatureHost {
        var attached = 0
        var detached = 0

        override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature {
            attached++
            return AttachedContentFeature { detached++ }
        }
    }

    private fun scene(collider: Boolean) = """
{ "version": 1, "name": "streamed", "nodes": [
  { "name": "World", "components": [
    { "component": "paged_terrain", "index": "world/index.json", "capacity": 2, "radius": 0, "maxConcurrentReads": 1,
      "collider": $collider, "collisionRadius": 0 }
  ] }
] }
"""

    private fun files(scene: String): AssetSource {
        // Three 4 m cells of five-square flat heights, over a coarse lattice four samples across.
        val index = TerrainPageIndex(
            minCellX = 0, minCellZ = 0, cellCountX = 3, cellCountZ = 1, cellSize = 4f, intervals = 4,
            minElevation = 0f, maxElevation = 10f, fallbackHeight = "coarse.raw", fallbackWidth = 4, fallbackDepth = 2,
            pages = List(3) { TerrainPageAssets(it, 0, "cell$it.raw") },
        )
        val cell = RawHeightmapCodec.encode16LittleEndian(Heightmap(FloatArray(25), 5, 5, Vec3f(1f, 1f, 1f)), 0f, 10f)
        val contents = mapOf(
            "awake.project.json" to MANIFEST.encodeToByteArray(),
            "scenes/main.scene.json" to scene.encodeToByteArray(),
            "world/index.json" to TerrainPageIndexCodec.encode(index),
            "world/coarse.raw" to RawHeightmapCodec.encode16LittleEndian(Heightmap(FloatArray(8), 4, 2, Vec3f(4f, 1f, 4f)), 0f, 10f),
        ) + List(3) { "world/cell$it.raw" to cell }
        return AssetSource { path -> runCatching { contents.getValue(path.value) } }
    }

    private companion object {
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        val DOWN = Vec3f(0f, -1f, 0f)
        const val STEP = 1f / 60f
        const val ATTEMPTS = 400
        const val WAIT_MILLIS = 5L
    }
}
