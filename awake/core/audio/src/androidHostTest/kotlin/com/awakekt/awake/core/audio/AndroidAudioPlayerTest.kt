/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

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
}
