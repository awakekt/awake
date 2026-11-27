/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class AndroidAudioPlayerTest {

    @Test
    fun playerInitializesWithExpectedDefaultVolumes() {
        val player = AndroidAudioPlayer()
        assertEquals(1.0f, player.masterVolume)
        assertEquals(1.0f, player.sfxVolume)
        assertEquals(0.6f, player.musicVolume)

        player.masterVolume = 0.5f
        player.sfxVolume = 0.8f
        player.musicVolume = 0.4f

        assertEquals(0.5f, player.masterVolume)
        assertEquals(0.8f, player.sfxVolume)
        assertEquals(0.4f, player.musicVolume)
        player.dispose()
    }

    @Test
    fun volumeInputsAreClampedBetweenZeroAndOne() {
        val player = AndroidAudioPlayer()
        player.masterVolume = -0.5f
        assertEquals(0.0f, player.masterVolume)

        player.masterVolume = 1.5f
        assertEquals(1.0f, player.masterVolume)

        player.sfxVolume = -1.0f
        assertEquals(0.0f, player.sfxVolume)

        player.sfxVolume = 2.0f
        assertEquals(1.0f, player.sfxVolume)

        player.musicVolume = -0.2f
        assertEquals(0.0f, player.musicVolume)

        player.musicVolume = 1.2f
        assertEquals(1.0f, player.musicVolume)
        player.dispose()
    }

    @Test
    fun invalidAndMalformedClipsAreSafelyRejected() {
        val player = AndroidAudioPlayer()
        val emptyClip = AudioClip(
            id = "empty",
            name = "Empty",
            pcmBytes = byteArrayOf(),
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
        )
        assertNull(player.playSound(emptyClip))

        val invalidRateClip = AudioClip(
            id = "zero_rate",
            name = "Zero Rate",
            pcmBytes = byteArrayOf(0, 0, 0, 0),
            sampleRate = 0,
            channels = 1,
            bitsPerSample = 16,
        )
        assertNull(player.playSound(invalidRateClip))

        val invalidChannelClip = AudioClip(
            id = "invalid_channels",
            name = "Invalid Channels",
            pcmBytes = byteArrayOf(0, 0, 0, 0),
            sampleRate = 44100,
            channels = 4,
            bitsPerSample = 16,
        )
        assertNull(player.playSound(invalidChannelClip))

        val invalidBitDepthClip = AudioClip(
            id = "invalid_bits",
            name = "Invalid Bits",
            pcmBytes = byteArrayOf(0, 0, 0, 0),
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 24,
        )
        assertNull(player.playSound(invalidBitDepthClip))
        player.dispose()
    }

    @Test
    fun soundHandleOperationsAndDisposalSafelyExecute() {
        val player = AndroidAudioPlayer()
        val clip = SoundSynthesizer.tone("test_tone", "Tone", 440f, 0.05f)

        // On host JVM test environment without android audio hardware, AudioTrack may fail to initialize
        // or return null gracefully. Neither playSound nor playMusic should throw exceptions.
        val handle = player.playSound(clip, volume = 0.8f, pan = -0.5f, loop = true)
        handle?.let {
            it.update(volume = 0.5f, pan = 0.5f)
            it.stop()
            assertFalse(it.isPlaying)
        }

        player.playMusic(clip, volume = 0.7f, loop = true)
        player.stopMusic()

        player.dispose()
        // Subsequent calls after disposal must safely return null without throwing
        assertNull(player.playSound(clip))
    }

    @Test
    fun customMaxConcurrentOneShotsIsValidatedAndOneShotPathSafelyExecutes() {
        assertFailsWith<IllegalArgumentException> {
            AndroidAudioPlayer(maxConcurrentOneShots = 0)
        }

        val player = AndroidAudioPlayer(maxConcurrentOneShots = 4)
        val clip = SoundSynthesizer.tone("oneshot_tone", "OneShot", 440f, 0.05f)
        val handle = player.playSound(clip, volume = 0.8f, pan = 0f, loop = false)
        handle?.stop()
        player.stopMusic()
        player.dispose()
        assertNull(player.playSound(clip, loop = false))
    }

    @Test
    fun playMusicStreamAppliesMasterAndMusicVolumesAndStopsCleanly() {
        val opened = mutableListOf<FakeStreamHandle>()
        val player = AndroidAudioPlayer(maxConcurrentOneShots = 4) { _, initialVolume, loop ->
            FakeStreamHandle(initialVolume, loop).also { opened += it }
        }
        val track = MusicStreamSource.EncodedBytes("bgm/town.mp3", byteArrayOf(1, 2, 3, 4))

        player.masterVolume = 0.5f
        player.musicVolume = 0.8f
        assertTrue(player.playMusicStream(track, volume = 0.5f, loop = true))
        assertEquals(1, opened.size)
        assertEquals(0.2f, opened[0].lastVolume, 0.0001f)
        assertTrue(opened[0].loop)
        assertTrue(opened[0].isPlaying)

        // Both masterVolume and musicVolume live-update the active music stream.
        player.masterVolume = 1.0f
        assertEquals(0.4f, opened[0].lastVolume, 0.0001f)
        player.musicVolume = 0.5f
        assertEquals(0.25f, opened[0].lastVolume, 0.0001f)

        // Starting a second stream stops the first stream before opening the replacement.
        val nextTrack = MusicStreamSource.FilePath("bgm/field", "/data/field.ogg")
        assertTrue(player.playMusicStream(nextTrack, volume = 1.0f, loop = false))
        assertEquals(1, opened[0].stopCount)
        assertFalse(opened[0].isPlaying)
        assertEquals(2, opened.size)
        assertEquals(0.5f, opened[1].lastVolume, 0.0001f)

        // Empty sources are rejected without opening a handle.
        assertFalse(player.playMusicStream(MusicStreamSource.EncodedBytes("empty.mp3", byteArrayOf())))
        assertFalse(player.playMusicStream(MusicStreamSource.FilePath("blank", " ")))

        // Dispose stops the active stream and rejects subsequent stream requests.
        player.dispose()
        assertEquals(1, opened[1].stopCount)
        assertFalse(player.playMusicStream(track))
    }

    @Test
    fun readMediaSliceCopiesRequestedRangesAndReportsEof() {
        val source = byteArrayOf(10, 20, 30, 40, 50)
        val dest = ByteArray(4)
        assertEquals(3, readMediaSlice(source, position = 1L, destination = dest, offset = 1, size = 3))
        assertContentEquals(byteArrayOf(0, 20, 30, 40), dest)
        assertEquals(1, readMediaSlice(source, position = 4L, destination = dest, offset = 0, size = 4))
        assertEquals(50, dest[0])
        assertEquals(-1, readMediaSlice(source, position = 5L, destination = dest, offset = 0, size = 4))
        assertEquals(-1, readMediaSlice(source, position = -1L, destination = dest, offset = 0, size = 4))
        assertEquals(0, readMediaSlice(source, position = 0L, destination = dest, offset = 0, size = 0))
    }

    private class FakeStreamHandle(
        initialVolume: Float,
        val loop: Boolean,
    ) : AndroidMusicStreamHandle {
        var lastVolume: Float = initialVolume
        var stopCount: Int = 0
        override var isPlaying: Boolean = true

        override fun setVolume(effectiveVolume: Float) {
            lastVolume = effectiveVolume
        }

        override fun stop() {
            stopCount++
            isPlaying = false
        }
    }
}
