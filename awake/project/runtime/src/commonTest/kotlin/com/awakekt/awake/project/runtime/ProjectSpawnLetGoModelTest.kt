/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A model a played scene lets go of the CPU copy of once it is uploaded can still be spawned again after its mesh left the
 * asset library: the spawn reads the model again and draws on the first frame after it has, rather than failing.
 */
class ProjectSpawnLetGoModelTest {
    @Test
    fun aModelSpawnedAgainAfterItsMeshWasDestroyedIsReadAgainAndDraws() = runTest {
        val (project, game, runtime) = play()
        val library = runtime.requireAssetLibrary()

        val first = runtime.spawn(project, lantern())
        game.updateUntil { runtime.world.get<MeshRenderer>(first.root) != null }
        assertTrue(project.models.isMeshLetGo(HELD), "its geometry went once its mesh was made")
        assertTrue(library.isMeshBuilt(HELD))

        first.despawn()
        game.update(DELTA, WIDTH, HEIGHT)
        assertFalse(library.isMeshBuilt(HELD), "with no retained budget the mesh goes with its last renderer")

        val again = runtime.spawn(project, lantern())
        assertNull(runtime.world.get<MeshRenderer>(again.root), "it waits for the model to be read again")
        game.updateUntil { runtime.world.get<MeshRenderer>(again.root) != null }
        assertNotNull(runtime.world.get<MeshRenderer>(again.root))
        game.dispose()
    }

    @Test
    fun aModelWhoseMeshIsStillBuiltDrawsOnTheFrameItSpawns() = runTest {
        val (project, game, runtime) = play()

        val first = runtime.spawn(project, lantern())
        game.updateUntil { runtime.world.get<MeshRenderer>(first.root) != null }
        val second = runtime.spawn(project, lantern())

        assertNotNull(runtime.world.get<MeshRenderer>(second.root), "the built mesh is shared at once, with no read")
        game.dispose()
    }

    @Test
    fun aBrokenSpawnedModelDoesNotBlockSubsequentModelsFromAttaching() = runTest {
        val (project, game, runtime) = play()

        val broken = runtime.spawn(project, brokenModel())
        val good = runtime.spawn(project, lantern())
        game.updateUntil { runtime.world.get<MeshRenderer>(good.root) != null }

        assertNull(runtime.world.get<MeshRenderer>(broken.root), "missing model fails cleanly without crashing")
        assertNotNull(runtime.world.get<MeshRenderer>(good.root), "subsequent healthy models still attach and draw")
        game.dispose()
    }

    private suspend fun play(): Triple<LoadedProject, AwakeAppLifecycle, SceneAppLifecycleRuntime> {
        val files = mapOf("awake.project.json" to MANIFEST, "scenes/main.scene.json" to SCENE, HELD to triangleGltf())
        val project = loadProject(AssetSource { path -> runCatching { files.getValue(path.value).encodeToByteArray() } })
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        game.update(DELTA, WIDTH, HEIGHT)
        return Triple(project, game, game.requireService<SceneAppLifecycleRuntime>())
    }

    /** Runs frames until [done], waiting between them on the loads that finish off the test thread. */
    private suspend fun AwakeAppLifecycle.updateUntil(done: () -> Boolean) {
        repeat(ATTEMPTS) {
            update(DELTA, WIDTH, HEIGHT)
            if (done()) return
            withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
        }
        fail("The model did not draw")
    }

    private fun lantern(): SceneNode = SceneLoader.decode(NODE).nodes.single()

    private fun brokenModel(): SceneNode = SceneLoader.decode(BROKEN_NODE).nodes.single()

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    /** One unskinned triangle, its buffer embedded. */
    private fun triangleGltf(): String {
        val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val buffer = ByteArray(positions.size * 4 + 8)
        positions.forEachIndexed { index, value ->
            val bits = value.toRawBits()
            for (byte in 0 until 4) buffer[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
        }
        // Indices 0, 1, 2 as unsigned shorts, padded to four bytes.
        buffer[positions.size * 4 + 2] = 1
        buffer[positions.size * 4 + 4] = 2
        return """
        {
          "asset": {"version": "2.0"},
          "buffers": [{"uri": "data:application/octet-stream;base64,${Base64.encode(buffer)}", "byteLength": ${buffer.size}}],
          "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 36}, {"buffer": 0, "byteOffset": 36, "byteLength": 6}],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
            {"bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR"}
          ],
          "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "indices": 1}]}],
          "nodes": [{"name": "Body", "mesh": 0}],
          "scenes": [{"nodes": [0]}],
          "scene": 0
        }
        """.trimIndent()
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val ATTEMPTS = 400
        const val WAIT_MILLIS = 5L
        const val MATERIAL = "lit-shadow"

        /** A model the scene never places, as an item picked up at runtime is. */
        const val HELD = "models/lantern.gltf"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """{ "version": 1, "name": "harbor", "nodes": [ { "name": "Dock" } ] }"""
        const val BROKEN = "models/missing.gltf"
        const val NODE = """
{ "version": 1, "nodes": [
  { "name": "Lantern", "components": [ { "component": "meshRenderer", "mesh": "$HELD", "material": "$MATERIAL" } ] }
] }
"""
        const val BROKEN_NODE = """
{ "version": 1, "nodes": [
  { "name": "Broken", "components": [ { "component": "meshRenderer", "mesh": "$BROKEN", "material": "$MATERIAL" } ] }
] }
"""
    }
}
