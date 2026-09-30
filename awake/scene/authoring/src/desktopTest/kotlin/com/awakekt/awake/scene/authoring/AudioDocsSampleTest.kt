/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.core.audio.AudioPlayer
import com.awakekt.awake.core.audio.NoOpAudioPlayer
import com.awakekt.awake.core.audio.SoundSynthesizer
import com.awakekt.awake.core.audio.WavDecoder
import com.awakekt.awake.core.audio.Waveform
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.audio.AudioSource
import com.awakekt.awake.scene.audio.AudioSystem
import com.awakekt.awake.scene.authoring.dsl.audioListener
import com.awakekt.awake.scene.authoring.dsl.audioSource
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.core.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Every sample on the "Audio" guide is a region here, run against the real audio system. */
class AudioDocsSampleTest {

    @Test
    fun aSourceNearTheListenerStartsPlaying() {
        val world = World()
        // --8<-- [start:clip]
        val chime = SoundSynthesizer.build(id = "chime", name = "Chime") {
            duration = 0.4f
            waveform = Waveform.SINE
            startFrequencyHz = 880f
            endFrequencyHz = 660f
        }
        // --8<-- [end:clip]
        // --8<-- [start:scene-dsl]
        world.scene {
            entity("camera") {
                transform(z = 5f)
                audioListener() // one per scene: spatial sounds are heard from here
            }
            entity("fountain") {
                transform(x = 3f)
                audioSource(chime, volume = 0.8f, maxDistance = 20f, loop = true)
            }
        }
        // --8<-- [end:scene-dsl]

        // --8<-- [start:system]
        val player: AudioPlayer = NoOpAudioPlayer() // JvmAudioPlayer() plays through Java Sound on desktop
        val audio = AudioSystem(player)
        audio.update(world, 1f / 60f) // autoPlay sources start on their first update
        // --8<-- [end:system]

        val fountain = world.source("fountain")
        assertTrue(fountain.isPlaying)
        assertEquals(0.8f, fountain.volume)
        assertTrue(fountain.loop)
    }

    @Test
    fun aSpatialSourceThatStartsOutOfRangeNeverAutoPlays() {
        val world = World()
        val chime = SoundSynthesizer.build("chime", "Chime") { duration = 0.2f }
        world.scene {
            entity("camera") { audioListener() }
            entity("far") {
                transform(x = 100f)
                audioSource(chime, maxDistance = 20f)
            }
        }
        val audio = AudioSystem(NoOpAudioPlayer())

        repeat(10) { audio.update(world, 1f / 60f) }

        assertFalse(world.source("far").isPlaying)
        assertTrue(world.source("far").hasAutoPlayed, "the one autoplay attempt is spent")
    }

    @Test
    fun aWavFileDecodesToAClip() {
        val bytes = pcm16Wav(samples = 441, sampleRate = 44100, channels = 1)
        // --8<-- [start:wav]
        val footstep = WavDecoder.decode(id = "footstep", name = "Footstep", bytes = bytes) // null if it is not an uncompressed PCM WAV
        // --8<-- [end:wav]
        assertEquals(1, assertNotNull(footstep).channels)
        assertEquals(44100, footstep.sampleRate)
        assertEquals(441 * 2, footstep.pcmBytes.size)
    }

    private fun World.source(name: String): AudioSource {
        var found: AudioSource? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = get<AudioSource>(entity) }
        return requireNotNull(found) { "no audio source named $name" }
    }

    /** A silent 16-bit PCM RIFF/WAVE file. */
    private fun pcm16Wav(samples: Int, sampleRate: Int, channels: Int): ByteArray {
        val data = samples * channels * 2
        val out = java.io.ByteArrayOutputStream()
        fun int(v: Int) = repeat(4) { out.write((v shr (8 * it)) and 0xFF) }
        fun short(v: Int) = repeat(2) { out.write((v shr (8 * it)) and 0xFF) }
        out.write("RIFF".toByteArray())
        int(36 + data)
        out.write("WAVEfmt ".toByteArray())
        int(16)
        short(1)
        short(channels)
        int(sampleRate)
        int(sampleRate * channels * 2)
        short(channels * 2)
        short(16)
        out.write("data".toByteArray())
        int(data)
        out.write(ByteArray(data))
        return out.toByteArray()
    }
}
