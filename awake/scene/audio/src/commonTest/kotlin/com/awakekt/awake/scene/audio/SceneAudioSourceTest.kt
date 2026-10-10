/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.audio

import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.audio.AudioPlayer
import com.awakekt.awake.core.audio.SoundHandle
import com.awakekt.awake.core.audio.WavDecoder
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneSerializers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A scene's `audio_source` loads as Core's own component: it decodes and saves back unchanged, its
 * clip is read from the project's files, and the audio system plays it once the clip has loaded.
 */
class SceneAudioSourceTest {
    private val json = SceneSerializers.createJson(mapOf(SceneAudioSource::class to SceneAudioSource.serializer()))

    @Test
    fun anAudioSourceLoadsAndSavesBackUnchanged() {
        val authored = SceneAudioSource(clip = WIND, volume = 0.5f, maxDistance = 30f, autoPlay = true, loop = true)
        val text = SceneLoader.encode(SceneDocument(name = "harbor", nodes = listOf(SceneNode(name = "Wind", components = listOf(authored)))), json)
        assertTrue("\"component\":\"audio_source\"" in text, text)
        val decoded = SceneLoader.decode(text, json).nodes.single().components.single()
        assertEquals(authored, decoded, "the scene's own component, as a game decodes it")

        val world = World()
        val entity = world.create()
        AudioSourceBinding.attachTyped(world, entity, authored, NO_CONTEXT)
        val source = assertNotNull(world.get<AudioSource>(entity))
        assertEquals(WIND, source.clip.id, "the clip is named, to load")
        assertTrue(source.clip.pcmBytes.isEmpty(), "and holds no samples until it has")
        assertEquals(authored, AudioSourceBinding.export(world, entity, source), "it saves back unchanged")
    }

    @Test
    fun eachClipIsReadOnceAndOneThatDoesNotLoadLeavesTheRestPlaying() = runTest {
        val reads = mutableListOf<String>()
        val files = AssetSource { path ->
            reads += path.value
            if (path.value == WIND) Result.success(wav()) else Result.failure(NoSuchElementException(path.value))
        }
        val scene = SceneDocument(
            name = "harbor",
            nodes = listOf(
                SceneNode(name = "Wind", components = listOf(SceneAudioSource(clip = WIND))),
                SceneNode(name = "Gust", components = listOf(SceneAudioSource(clip = WIND))),
                SceneNode(name = "Bell", components = listOf(SceneAudioSource(clip = "assets/audio/missing.wav"))),
                SceneNode(name = "Unpicked", components = listOf(SceneAudioSource())),
            ),
        )

        val clips = loadAudioClips(scene, files)

        assertEquals(setOf(WIND), clips.keys)
        assertEquals(listOf(WIND, "assets/audio/missing.wav"), reads, "each clip read once, and nothing for one not yet picked")
        assertTrue(clips.getValue(WIND).pcmBytes.isNotEmpty())
    }

    @Test
    fun theAudioSystemPlaysASourceOnceItsClipHasLoaded() {
        val world = World()
        val entity = world.create()
        world.add(entity, Transform())
        AudioSourceBinding.attachTyped(world, entity, SceneAudioSource(clip = WIND, isSpatial3D = false, autoPlay = true), NO_CONTEXT)
        val player = RecordingPlayer()
        val clip = requireNotNull(WavDecoder.decode(WIND, WIND, wav())) { "the test WAV didn't decode" }
        val audio = AudioSystem(player, clips = mapOf(WIND to clip))

        repeat(2) { audio.update(world, FRAME) }

        assertEquals(listOf(WIND), player.played.map { it.id }, "it plays the decoded clip, once")
        assertTrue(player.played.single().pcmBytes.isNotEmpty())
    }

    private class RecordingPlayer : AudioPlayer {
        val played = mutableListOf<AudioClip>()
        override var masterVolume = 1f
        override var sfxVolume = 1f
        override var musicVolume = 1f

        override fun playSound(clip: AudioClip, volume: Float, pan: Float, loop: Boolean): SoundHandle {
            played += clip
            return object : SoundHandle {
                override val isPlaying = true

                override fun stop() = Unit
            }
        }

        override fun playMusic(clip: AudioClip, volume: Float, loop: Boolean) = Unit

        override fun stopMusic() = Unit

        override fun dispose() = Unit
    }

    private companion object {
        const val WIND = "assets/audio/wind.wav"
        const val FRAME = 1f / 60f
        val NO_CONTEXT = object : SceneResolutionContext {
            override val world = World()

            override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

            override fun recordRequest(request: Any) = Unit
        }

        /** A tenth of a second of 16-bit mono silence at 8 kHz, as a WAV file. */
        fun wav(): ByteArray {
            val samples = 800
            val data = samples * 2
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
