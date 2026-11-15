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
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.ScenePrewarm
import com.awakekt.awake.scene.runtime.SpawnedNode
import com.awakekt.awake.scene.runtime.prewarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A held prewarm of an effect whose `.gltf` has a sidecar `.bin` and image, spawned and despawned over
 * [FRAMES] frames as a game fires an effect, reads each of the model's files once (#1621).
 *
 * Measured on main: the model once, as the prewarm and spawns share its load, but the `.bin` and the image
 * 3 times each, one read per stage of that one load. A game whose effects share sidecar files multiplies
 * that by the models sharing them.
 */
class HeldPrewarmReadsTest {
    private val reads = mutableListOf<String>()
    private val lock = Mutex()

    @Test
    fun aHeldEffectIsReadOnceHoweverOftenItSpawns() = runTest {
        val effect by lazy { SceneLoader.decode(EFFECT).nodes.single() }
        var prewarm: ScenePrewarm? = null
        var live: SpawnedNode? = null
        var frame = 0
        var spawns = 0
        val project = loadProject(
            AssetSource { path ->
                lock.withLock { reads += path.value }
                FILES[path.value]?.let { Result.success(it) } ?: Result.failure(NoSuchElementException(path.value))
            },
            capabilities = listOf(object : SceneFeature {
                override val id = "com.example.effects"

                override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
                    plan.frame("effects") { services ->
                        object : System {
                            override fun update(world: World, delta: Float) {
                                frame++
                                val hold = prewarm ?: services.prewarm(effect).also { prewarm = it }
                                if (hold.isReady && frame % EVERY == 0) {
                                    live?.despawn()
                                    live = services.spawn(effect)
                                    spawns++
                                }
                            }
                        }
                    }
                }
            }),
        )
        val game = app { scene("play") { runProject(project) } }
        game.ready(Renderer())
        try {
            repeat(FRAMES) {
                game.update(DELTA, WIDTH, HEIGHT)
                withContext(Dispatchers.Default) { delay(1) }
            }
            game.requireService<SceneAppLifecycleRuntime>()
            val counts = lock.withLock { reads.groupingBy { it }.eachCount() }
            println("Over $FRAMES frames, $spawns spawns of a held effect: reads $counts")
            assertEquals(1, counts[MODEL], "the model file is read once")
            assertEquals(1, counts[BIN], "its buffer once")
            assertEquals(1, counts[IMAGE], "its image once")
        } finally {
            prewarm?.close()
            game.dispose()
        }
    }

    private class Renderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val FRAMES = 600
        const val EVERY = 30
        const val MODEL = "effects/glow.gltf"
        const val BIN = "effects/glow.bin"
        const val IMAGE = "effects/glow.png"
        const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        const val EFFECT = """
{ "version": 1, "nodes": [
  { "name": "Glow", "components": [ { "component": "meshRenderer", "mesh": "$MODEL", "material": "lit-shadow" } ] }
] }
"""
        val FILES: Map<String, ByteArray> by lazy {
            val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
            val buffer = ByteArray(positions.size * 4 + 8)
            positions.forEachIndexed { index, value ->
                val bits = value.toRawBits()
                for (byte in 0 until 4) buffer[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
            }
            buffer[positions.size * 4 + 2] = 1
            buffer[positions.size * 4 + 4] = 2
            val gltf = """
            {
              "asset": {"version": "2.0"},
              "buffers": [{"uri": "glow.bin", "byteLength": ${buffer.size}}],
              "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 36}, {"buffer": 0, "byteOffset": 36, "byteLength": 6}],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
                {"bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR"}
              ],
              "images": [{"uri": "glow.png"}],
              "textures": [{"source": 0}],
              "materials": [{"pbrMetallicRoughness": {"baseColorTexture": {"index": 0}}}],
              "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "indices": 1, "material": 0}]}],
              "nodes": [{"name": "Body", "mesh": 0}],
              "scenes": [{"nodes": [0]}],
              "scene": 0
            }
            """.trimIndent()
            mapOf(
                "awake.project.json" to """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}""".encodeToByteArray(),
                "scenes/main.scene.json" to """{ "version": 1, "name": "harbor", "nodes": [ { "name": "Dock" } ] }""".encodeToByteArray(),
                MODEL to gltf.encodeToByteArray(),
                BIN to buffer,
                IMAGE to Base64.Default.decode(PNG),
            )
        }
    }
}
