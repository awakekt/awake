/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OfflineAudioMixerTest {

    @Test
    fun mixesSfxAndMusicIntoValidDecodableWav() {
        val mixer = OfflineAudioMixer(sampleRate = 48_000)
        val music = SoundSynthesizer.tone("music", "music", frequencyHz = 220f, durationSeconds = 1.0f)
        val hit = SoundSynthesizer.tone("hit", "hit", frequencyHz = 440f, durationSeconds = 0.25f)

        mixer.playMusic(music, volume = 0.5f, loop = true)
        repeat(30) { frame ->
            if (frame == 15) {
                mixer.playSound(hit, volume = 0.8f, pan = 0.5f)
            }
            mixer.advance(1f / 60f)
        }

        assertEquals(24_000, mixer.mixedFrameCount)
        assertEquals(0.5f, mixer.mixedDurationSeconds, 1e-3f)
        assertTrue(mixer.peakAmplitude() > 0.2f, "Expected audible peak, got ${mixer.peakAmplitude()}")
        assertTrue(mixer.rmsEnergy() > 0.05f, "Expected non-silent RMS, got ${mixer.rmsEnergy()}")

        val wav = mixer.encodeWav()
        val decoded = WavDecoder.decode("mixed", "mixed.wav", wav)
        assertNotNull(decoded, "Encoded WAV must decode cleanly via WavDecoder")
        assertEquals(48_000, decoded.sampleRate)
        assertEquals(2, decoded.channels)
        assertEquals(16, decoded.bitsPerSample)
        assertEquals(24_000 * 2 * 2, decoded.pcmBytes.size)
    }

    @Test
    fun dynamicDuckingReducesMusicEnergyUnderStinger() {
        val music = SoundSynthesizer.tone("bgm", "bgm", frequencyHz = 220f, durationSeconds = 1.0f)

        val unducked = OfflineAudioMixer(sampleRate = 48_000)
        unducked.playMusic(music, volume = 0.8f, loop = true)
        repeat(30) { unducked.advance(1f / 60f) }

        val ducked = OfflineAudioMixer(sampleRate = 48_000)
        ducked.playMusic(music, volume = 0.8f, loop = true)
        ducked.triggerDuck(gainLinear = 0.2f, holdSeconds = 0.4f, attackSeconds = 0.01f, releaseSeconds = 0.05f)
        repeat(30) { ducked.advance(1f / 60f) }

        assertTrue(
            ducked.rmsEnergy() < unducked.rmsEnergy() * 0.5f,
            "Ducked RMS (${ducked.rmsEnergy()}) should be substantially lower than unducked (${unducked.rmsEnergy()})",
        )
    }

    @Test
    fun hardRightPanConcentratesEnergyInRightChannel() {
        val mixer = OfflineAudioMixer(sampleRate = 48_000)
        val tone = SoundSynthesizer.tone("sfx", "sfx", frequencyHz = 440f, durationSeconds = 0.2f)
        mixer.playSound(tone, volume = 0.9f, pan = 1f)
        repeat(6) { mixer.advance(1f / 60f) }

        val decoded = assertNotNull(WavDecoder.decode("pan", "pan.wav", mixer.encodeWav()))
        var leftSum = 0L
        var rightSum = 0L
        val pcm = decoded.pcmBytes
        var i = 0
        while (i + 3 < pcm.size) {
            val l = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            val r = (pcm[i + 2].toInt() and 0xFF) or (pcm[i + 3].toInt() shl 8)
            leftSum += kotlin.math.abs(l)
            rightSum += kotlin.math.abs(r)
            i += 4
        }
        assertTrue(rightSum > 10_000, "Right channel should carry strong signal")
        assertTrue(leftSum < rightSum / 100, "Hard-right pan should leave left channel near silent ($leftSum vs $rightSum)")
    }
}
