/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneAssetReferences
import com.awakekt.awake.scene.runtime.ScenePrewarm
import com.awakekt.awake.scene.runtime.SpawnedNode
import com.awakekt.awake.scene.runtime.assetReferences
import com.awakekt.awake.scene.runtime.prewarm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** A prewarm loads a glTF model the scene didn't, off the frame thread, so a spawn of it draws at once. */
class ProjectPrewarmTest {
    private val reads = mutableListOf<String>()
    private val readsLock = Mutex()

    // Reads of HELD wait here when a test holds them; one cancelled while it waits completes [readCancelled].
    private var held: CompletableDeferred<Unit>? = null
    private val readCancelled = CompletableDeferred<Unit>()

    @Test
    fun aNodePrewarmedWithItsModelDrawsOnTheFrameItSpawns() = runTest {
        val effect by lazy { node(HELD) }
        val progress = mutableListOf<Pair<Int, Int>>()
        var prewarm: ScenePrewarm? = null
        var batch: ScenePrewarm? = null
        var spawned: SpawnedNode? = null
        val (_, game, runtime) = play { services ->
            val hold = prewarm
            if (hold == null) {
                assertTrue(services.canPrewarm)
                assertTrue(services.canPrewarmAssets)
                prewarm = services.prewarm(effect)
                batch = services.prewarm(effect.assetReferences() + node(MISSING).assetReferences()) { settled, total ->
                    progress += settled to total
                }
            } else if (hold.isReady && batch?.isReady == true && spawned == null) {
                spawned = services.spawn(effect)
            }
        }
        try {
            game.updateUntil { spawned != null }
            val hold = assertNotNull(prewarm)
            val batchHold = assertNotNull(batch)
            assertEquals(emptyMap(), hold.failed)
            assertEquals(setOf("$MISSING + $MATERIAL"), batchHold.failed.keys)
            assertEquals(listOf(1 to 2, 2 to 2), progress)

            val instance = assertNotNull(spawned)
            val drawn = assertNotNull(runtime.world.get<MeshRenderer>(instance.root), "it draws on the spawn frame")
            assertTrue(instance.unattachedRenderables.isEmpty(), "no renderer waits for its model")
            val library = runtime.requireAssetLibrary()
            assertEquals(HELD, library.meshName(drawn.mesh))
            assertEquals(3, library.meshHolderCount(HELD), "the spawn and both holds share one mesh")
            hold.close()
            batchHold.close()
            assertEquals(1, library.meshHolderCount(HELD))
            assertEquals(1, readsOf(HELD))
        } finally {
            game.dispose()
        }
    }

    @Test
    fun headlessServicesAdvertiseOnlyThePrewarmerTheHostInstalls() {
        val services = SceneHostServices.headless(input = { error("prewarming does not read input") })
        val node = SceneNode(name = "Lantern")
        val references = SceneAssetReferences()
        assertFalse(services.canPrewarm)
        assertFalse(services.canPrewarmAssets)
        assertFailsWith<IllegalStateException> { services.prewarm(node) }
        assertFailsWith<IllegalStateException> { services.prewarm(references) }

        val custom = services.withPrewarmer { requested, _ ->
            assertEquals(node, requested)
            error("custom prewarmer")
        }
        assertTrue(custom.canPrewarm)
        assertFalse(custom.canPrewarmAssets)
        assertEquals("custom prewarmer", assertFailsWith<IllegalStateException> { custom.prewarm(node) }.message)
    }

    @Test
    fun prewarmsAndASpawnOfOneModelShareOneLoad() = runTest {
        held = CompletableDeferred()
        val (project, game, runtime) = play()
        val first = runtime.prewarm(node(HELD))
        val second = runtime.prewarm(node(HELD))
        game.update(DELTA, WIDTH, HEIGHT)
        val spawned = runtime.spawn(project, node(HELD))
        assertNull(runtime.world.get<MeshRenderer>(spawned.root), "the spawn waits for the load under way")

        held?.complete(Unit)
        game.updateUntil { first.isReady && second.isReady && runtime.world.get<MeshRenderer>(spawned.root) != null }

        assertEquals(1, readsOf(HELD), "the model is read and decoded once")
        first.close()
        second.close()
    }

    @Test
    fun closingAPrewarmCancelsItsModelLoad() = runTest {
        held = CompletableDeferred()
        val (project, game, runtime) = play()
        val prewarm = runtime.prewarm(node(HELD))
        game.updateUntil { readsOf(HELD) == 1 }

        prewarm.close()

        awaitReal { readCancelled.await() }
        game.update(DELTA, WIDTH, HEIGHT)
        assertFalse(project.models.isPreloaded(HELD), "a cancelled load adopts nothing")
    }

    @Test
    fun tearingTheSceneDownCancelsAPrewarmsModelLoad() = runTest {
        held = CompletableDeferred()
        val (_, game, runtime) = play()
        val prewarm = runtime.prewarm(node(HELD))
        game.updateUntil { readsOf(HELD) == 1 }

        game.dispose()

        awaitReal { readCancelled.await() }
        assertFailsWith<CancellationException> { prewarm.await() }
    }

    @Test
    fun aModelThatCantLoadIsNamedInFailed() = runTest {
        val (project, game, runtime) = play()
        val prewarm = runtime.prewarm(node(MISSING))
        game.updateUntil { prewarm.isReady }

        assertEquals(setOf("$MISSING + $MATERIAL"), prewarm.failed.keys)
        assertFalse(project.models.isPreloaded(MISSING))
        prewarm.close()
    }

    private suspend fun play(onFrame: ((SceneHostServices) -> Unit)? = null): Triple<LoadedProject, AwakeAppLifecycle, SceneAppLifecycleRuntime> {
        val files = mapOf("awake.project.json" to MANIFEST, "scenes/main.scene.json" to SCENE, HELD to triangleGltf())
        val capabilities = if (onFrame == null) {
            emptyList()
        } else {
            listOf(object : SceneCapability {
                override val id = "com.example.prewarm"

                override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
                    plan.frame("test-prewarm") { services ->
                        object : System {
                            override fun update(world: World, delta: Float) = onFrame(services)
                        }
                    }
                }
            })
        }
        val project = loadProject(
            AssetSource { path ->
                readsLock.withLock { reads += path.value }
                if (path.value == HELD) holdRead()
                runCatching { files.getValue(path.value).encodeToByteArray() }
            },
            capabilities = capabilities,
        )
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        game.update(DELTA, WIDTH, HEIGHT)
        return Triple(project, game, game.requireService<SceneAppLifecycleRuntime>())
    }

    private suspend fun holdRead() {
        val gate = held ?: return
        try {
            gate.await()
        } catch (cancelled: CancellationException) {
            readCancelled.complete(Unit)
            throw cancelled
        }
    }

    private suspend fun readsOf(path: String): Int = readsLock.withLock { reads.count { it == path } }

    /** Runs frames until [done], waiting between them on the loads that finish off the test thread. */
    private suspend fun AwakeAppLifecycle.updateUntil(done: suspend () -> Boolean) {
        repeat(ATTEMPTS) {
            update(DELTA, WIDTH, HEIGHT)
            if (done()) return
            withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
        }
        fail("The prewarm did not finish")
    }

    // In real time: the loads run on the session's dispatcher, not the test's virtual clock.
    private suspend fun awaitReal(block: suspend () -> Unit) = withContext(Dispatchers.Default) { withTimeout(TIMEOUT_MILLIS) { block() } }

    private fun node(model: String): SceneNode = SceneLoader.decode(REMOTE.replace("\"$HELD\"", "\"$model\"")).nodes.single()

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
        const val TIMEOUT_MILLIS = 5_000L
        const val MATERIAL = "lit-shadow"

        /** A model the scene never places, as an item picked up at runtime is. */
        const val HELD = "models/lantern.gltf"
        const val MISSING = "models/missing.gltf"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """{ "version": 1, "name": "harbor", "nodes": [ { "name": "Dock" } ] }"""
        const val REMOTE = """
{ "version": 1, "nodes": [
  { "name": "Lantern", "components": [ { "component": "meshRenderer", "mesh": "$HELD", "material": "$MATERIAL" } ] }
] }
"""
    }
}
