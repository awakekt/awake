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
import com.awakekt.awake.scene.runtime.minShadowCasterRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Verifies per-frame model attach budgeting in [SpawnedModels], retained mesh reuse across
 * cell boundaries via [com.awakekt.awake.scene.authoring.SceneAppDsl.retainedMeshBudgetBytes],
 * and off-screen shadow caster culling via [com.awakekt.awake.scene.authoring.SceneAppDsl.minShadowCasterRatio].
 */
class SpawnedModelsBudgetTest {
    @Test
    fun burstModelAttachesSpreadAcrossFramesWithinBudget() = runTest {
        val modelPaths = (1..MODEL_COUNT).map { i -> "models/prop_$i.gltf" }
        val files = buildMap {
            put("awake.project.json", MANIFEST)
            put("scenes/main.scene.json", SCENE)
            val gltf = triangleGltf()
            modelPaths.forEach { put(it, gltf) }
        }
        val project = loadProject(AssetSource { path -> runCatching { files.getValue(path.value).encodeToByteArray() } })
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        game.update(DELTA, WIDTH, HEIGHT)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()

        val spawned = modelPaths.map { path -> runtime.spawn(project, propNode(path)) }
        // Let background reads complete before stepping the frame thread so all 10 are ready at once.
        withContext(Dispatchers.Default) { delay(BURST_LOAD_WAIT_MILLIS) }

        fun attachedCount(): Int = spawned.count { runtime.world.get<MeshRenderer>(it.root) != null }

        // Frame 1 must attach at most DEFAULT_MAX_ATTACHES_PER_FRAME models, never all 10 at once.
        var previous = 0
        var frames = 0
        while (attachedCount() < MODEL_COUNT && frames < MAX_ATTEMPTS) {
            game.update(DELTA, WIDTH, HEIGHT)
            frames += 1
            val current = attachedCount()
            val added = current - previous
            assertTrue(
                added <= SpawnedModels.DEFAULT_MAX_ATTACHES_PER_FRAME,
                "Frame $frames attached $added models, exceeding cap ${SpawnedModels.DEFAULT_MAX_ATTACHES_PER_FRAME}",
            )
            previous = current
            if (current < MODEL_COUNT && added == 0) {
                withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
            }
        }
        assertEquals(MODEL_COUNT, attachedCount(), "All $MODEL_COUNT burst models must eventually attach")
        assertTrue(frames >= EXPECTED_MIN_FRAMES, "Expected burst to span at least $EXPECTED_MIN_FRAMES frames, took $frames")
        game.dispose()
    }

    @Test
    fun retainedMeshBudgetKeepsReleasedMeshForInstantReattach() = runTest {
        val files = mapOf("awake.project.json" to MANIFEST, "scenes/main.scene.json" to SCENE, HELD to triangleGltf())
        val project = loadProject(AssetSource { path -> runCatching { files.getValue(path.value).encodeToByteArray() } })
        val game = app {
            scene("play") {
                runProject(project)
                retainedMeshBudgetBytes(RETAINED_BUDGET_BYTES)
                minShadowCasterRatio(SHADOW_RATIO)
            }
        }
        game.ready(TestRenderer())
        game.update(DELTA, WIDTH, HEIGHT)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val library = runtime.requireAssetLibrary()

        assertEquals(SHADOW_RATIO, runtime.minShadowCasterRatio)
        runtime.minShadowCasterRatio = UPDATED_SHADOW_RATIO
        assertEquals(UPDATED_SHADOW_RATIO, runtime.minShadowCasterRatio)

        val first = runtime.spawn(project, propNode(HELD))
        game.updateUntil { runtime.world.get<MeshRenderer>(first.root) != null }
        assertTrue(library.isMeshBuilt(HELD))

        // Despawning the sole holder retains the mesh instead of destroying it.
        first.despawn()
        game.update(DELTA, WIDTH, HEIGHT)
        assertTrue(library.isMeshRetained(HELD), "Retained mesh budget must keep released mesh for cell-seam reuse")
        assertTrue(library.isMeshBuilt(HELD))

        // Re-spawning revives the retained mesh immediately on the spawn frame without an async reload.
        val again = runtime.spawn(project, propNode(HELD))
        assertNotNull(runtime.world.get<MeshRenderer>(again.root), "Retained mesh must revive synchronously on spawn")
        game.dispose()
    }

    private suspend fun AwakeAppLifecycle.updateUntil(done: () -> Boolean) {
        repeat(MAX_ATTEMPTS) {
            update(DELTA, WIDTH, HEIGHT)
            if (done()) return
            withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
        }
        fail("Condition was not met within $MAX_ATTEMPTS frames")
    }

    private fun propNode(path: String): SceneNode = SceneLoader.decode(
        """
        { "version": 1, "nodes": [
          { "name": "Prop", "components": [ { "component": "meshRenderer", "mesh": "$path", "material": "$MATERIAL" } ] }
        ] }
        """.trimIndent(),
    ).nodes.single()

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private fun triangleGltf(): String {
        val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val buffer = ByteArray(positions.size * 4 + 8)
        positions.forEachIndexed { index, value ->
            val bits = value.toRawBits()
            for (byte in 0 until 4) buffer[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
        }
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
        const val MODEL_COUNT = 10
        const val EXPECTED_MIN_FRAMES = 3
        const val MAX_ATTEMPTS = 400
        const val WAIT_MILLIS = 5L
        const val BURST_LOAD_WAIT_MILLIS = 150L
        const val RETAINED_BUDGET_BYTES = 1024L * 1024L
        const val SHADOW_RATIO = 0.05f
        const val UPDATED_SHADOW_RATIO = 0.08f
        const val MATERIAL = "lit-shadow"
        const val HELD = "models/lantern.gltf"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """{ "version": 1, "name": "harbor", "nodes": [ { "name": "Dock" } ] }"""
    }
}
