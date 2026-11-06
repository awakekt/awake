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
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.audio.AudioSource
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.audioClips
import com.awakekt.awake.scene.runtime.particleImages
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A played project's scene gives the runtime's asset library its clips and particle images, so a prewarm
 * of a node decodes into what the scene's systems play and draw, with no code in the host.
 */
class ProjectPrewarmLoadsTest {
    private val reads = mutableListOf<String>()
    private val readsLock = Mutex()

    @Test
    fun aPrewarmInAPlayedSceneSharesTheLoadsItsSystemsUse() = runTest {
        val (project, game, runtime) = play()
        val library = runtime.requireAssetLibrary()
        assertNotNull(library.audioClips, "the played scene registered its clips")
        assertNotNull(library.particleImages, "and its particle images")

        val prewarm = runtime.prewarm(node())
        game.updateUntil { prewarm.isReady }
        assertEquals(emptyMap(), prewarm.failed)
        assertEquals(2, prewarm.total)

        val spawned = runtime.spawn(project, node())
        game.update(DELTA, WIDTH, HEIGHT)

        val source = assertNotNull(runtime.world.get<AudioSource>(spawned.root))
        assertTrue(source.clip.pcmBytes.isNotEmpty(), "the clip plays on the spawn's first frame")
        assertNotNull(runtime.world.get<ParticleEmitter>(spawned.root), "the emitter draws on the spawn's first frame")
        assertEquals(1, readsOf(BELL), "the prewarm and the audio system share one decode")
        assertEquals(1, readsOf(SMOKE), "the prewarm and the particle system share one read")
        assertEquals(1, readsOf(WIND), "a clip the scene loaded is not read again")
        prewarm.close()
    }

    @Test
    fun theLoadsLeaveTheLibraryWhenTheSceneStops() = runTest {
        val (_, game, runtime) = play()
        val library = runtime.requireAssetLibrary()

        game.dispose()

        assertNull(library.audioClips)
        assertNull(library.particleImages)
    }

    private suspend fun play(): Triple<LoadedProject, AwakeAppLifecycle, SceneAppLifecycleRuntime> {
        val text = mapOf("awake.project.json" to MANIFEST, "scenes/main.scene.json" to SCENE)
        val project = loadProject(
            AssetSource { path ->
                readsLock.withLock { reads += path.value }
                runCatching {
                    when (path.value) {
                        WIND, BELL -> wav()
                        SPARK, SMOKE -> Base64.decode(PIXEL)
                        else -> text.getValue(path.value).encodeToByteArray()
                    }
                }
            },
        )
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        game.update(DELTA, WIDTH, HEIGHT)
        return Triple(project, game, game.requireService<SceneAppLifecycleRuntime>())
    }

    private suspend fun readsOf(path: String): Int = readsLock.withLock { reads.count { it == path } }

    /** Runs frames until [done], waiting between them on the loads that finish off the test thread. */
    private suspend fun AwakeAppLifecycle.updateUntil(done: () -> Boolean) {
        repeat(ATTEMPTS) {
            update(DELTA, WIDTH, HEIGHT)
            if (done()) return
            withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
        }
        fail("The prewarm did not finish")
    }

    private fun node() = SceneLoader.decode(REMOTE).nodes.single()

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val ATTEMPTS = 400
        const val WAIT_MILLIS = 5L

        /** What the scene plays and shows itself, read as it loads. */
        const val WIND = "audio/wind.wav"
        const val SPARK = "particles/spark.png"

        /** What only the node a game spawns later names. */
        const val BELL = "audio/bell.wav"
        const val SMOKE = "particles/smoke.png"

        /** A one-pixel PNG. */
        const val PIXEL = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Wind", "components": [ { "component": "audio_source", "clip": "$WIND", "isSpatial3D": false } ] },
  { "name": "Sparks", "components": [ { "component": "particle_emitter", "texture": "$SPARK" } ] }
] }
"""
        const val REMOTE = """
{ "version": 1, "nodes": [
  { "name": "Bell", "components": [
    { "component": "audio_source", "clip": "$BELL", "isSpatial3D": false },
    { "component": "particle_emitter", "texture": "$SMOKE" }
  ] }
] }
"""

        /** A tenth of a second of 16-bit mono silence at 8 kHz. */
        fun wav(): ByteArray {
            val data = 1600
            val bytes = ByteArray(44 + data)
            fun ascii(at: Int, text: String) = text.forEachIndexed { i, c -> bytes[at + i] = c.code.toByte() }
            fun int(at: Int, value: Int, size: Int) = repeat(size) { bytes[at + it] = (value shr (8 * it)).toByte() }
            ascii(0, "RIFF")
            int(4, 36 + data, 4)
            ascii(8, "WAVE")
            ascii(12, "fmt ")
            int(16, 16, 4)
            int(20, 1, 2)
            int(22, 1, 2)
            int(24, 8000, 4)
            int(28, 16000, 4)
            int(32, 2, 2)
            int(34, 16, 2)
            ascii(36, "data")
            int(40, data, 4)
            return bytes
        }
    }
}
