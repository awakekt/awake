/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.audio

import io.github.awakelab.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AudioCoreTest {

    @Test
    fun soundSynthesizerBuilderProducesValidClip() {
        val clip = SoundSynthesizer.build("test_laser", "Laser") {
            duration = 0.2f
            waveform = Waveform.SINE
            startFrequencyHz = 880f
            endFrequencyHz = 220f
            envelope = AdsrEnvelope(attack = 0.01f, decay = 0.19f)
        }

        assertNotNull(clip)
        assertEquals("test_laser", clip.id)
        assertEquals(44100, clip.sampleRate)
        assertEquals(1, clip.channels)
        assertTrue(clip.pcmBytes.isNotEmpty())
        assertTrue(clip.durationSeconds in 0.19f..0.21f)
    }

    @Test
    fun soundSynthesizerConvenienceGeneratorsWork() {
        val tone = SoundSynthesizer.tone("tone_440", "A440", 440f, 0.1f)
        assertNotNull(tone)
        assertTrue(tone.pcmBytes.isNotEmpty())

        val noise = SoundSynthesizer.noise("noise_white", "Noise", 0.1f)
        assertNotNull(noise)
        assertTrue(noise.pcmBytes.isNotEmpty())

        val arpeggio = SoundSynthesizer.arpeggio("arp_test", "Arp", listOf(440f, 550f, 660f), 0.1f)
        assertNotNull(arpeggio)
        assertTrue(arpeggio.pcmBytes.isNotEmpty())
    }

    @Test
    fun positionalAudioMathComputesAttenuationAndPan() {
        assertEquals(1.0f, PositionalAudioMath.computeAttenuation(0f, 50f))
        assertEquals(0f, PositionalAudioMath.computeAttenuation(60f, 50f))

        val mid = PositionalAudioMath.computeAttenuation(25f, 50f)
        assertTrue(mid in 0.3f..0.6f)

        val listenerPos = Vec3f(0f, 0f, 0f)
        val listenerRight = Vec3f(1f, 0f, 0f)
        val rightEmitter = Vec3f(10f, 0f, 0f)
        val panRight = PositionalAudioMath.computePan(rightEmitter, listenerPos, listenerRight)
        assertEquals(1.0f, panRight)

        val leftEmitter = Vec3f(-10f, 0f, 0f)
        val panLeft = PositionalAudioMath.computePan(leftEmitter, listenerPos, listenerRight)
        assertEquals(-1.0f, panLeft)
    }

    @Test
    fun noOpAudioPlayerSafelyOperatesWithoutExceptions() {
        val player = NoOpAudioPlayer()
        player.masterVolume = 0.8f
        player.sfxVolume = 0.9f
        player.musicVolume = 0.5f

        assertEquals(0.8f, player.masterVolume)
        val clip = SoundSynthesizer.tone("test_tone", "Tone", 440f, 0.05f)
        val handle = player.playSound(clip)
        assertNotNull(handle)
        handle.stop()
        player.playMusic(clip)
        player.stopMusic()
        player.dispose()
    }
}
