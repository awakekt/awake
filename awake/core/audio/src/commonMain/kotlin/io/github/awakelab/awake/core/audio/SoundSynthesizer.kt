/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class Waveform {
    SINE,
    SQUARE,
    TRIANGLE,
    SAWTOOTH,
    NOISE,
}

data class AdsrEnvelope(
    val attack: Float = 0.05f,
    val decay: Float = 0.15f,
    val sustain: Float = 0.0f,
    val release: Float = 0.0f,
) {
    fun amplitudeAt(timeSeconds: Float, totalDuration: Float): Float {
        if (timeSeconds < 0f || timeSeconds > totalDuration) return 0f
        if (timeSeconds < attack && attack > 0f) {
            return (timeSeconds / attack).coerceIn(0f, 1f)
        }
        val postAttack = timeSeconds - attack
        if (postAttack < decay && decay > 0f) {
            val progress = postAttack / decay
            return (1f - (1f - sustain) * progress).coerceIn(0f, 1f)
        }
        val sustainDuration = (totalDuration - attack - decay - release).coerceAtLeast(0f)
        val postSustain = postAttack - decay
        if (postSustain < sustainDuration) {
            return sustain.coerceIn(0f, 1f)
        }
        val postRelease = postSustain - sustainDuration
        if (release > 0f) {
            return (sustain * (1f - postRelease / release)).coerceIn(0f, 1f)
        }
        return 0f
    }
}

/**
 * Specification builder for programmatic procedural audio synthesis.
 */
class SoundSpecBuilder(val id: String, val name: String) {
    var duration: Float = 0.2f
    var sampleRate: Int = 44100
    var waveform: Waveform = Waveform.SINE
    var startFrequencyHz: Float = 440f
    var endFrequencyHz: Float? = null
    var envelope: AdsrEnvelope = AdsrEnvelope()
    var lowPassFilter: Float = 0f
    var volume: Float = 0.85f
}

/**
 * Pure Kotlin Multiplatform procedural sound synthesizer.
 *
 * Provides a generic builder for generating custom waveforms, pitch sweeps, and envelopes.
 */
object SoundSynthesizer {

    private const val TWO_PI = (2.0 * PI).toFloat()

    /**
     * Builds a custom [AudioClip] using the procedural synthesis DSL.
     */
    fun build(id: String, name: String, block: SoundSpecBuilder.() -> Unit): AudioClip {
        val spec = SoundSpecBuilder(id, name).apply(block)
        val numSamples = (spec.sampleRate * spec.duration).toInt().coerceAtLeast(1)
        val pcm = ByteArray(numSamples * 2)

        val random = Random(spec.id.hashCode())
        var phase = 0f
        var lastFiltered = 0f

        for (i in 0 until numSamples) {
            val t = i.toFloat() / spec.sampleRate
            val progress = i.toFloat() / numSamples

            val currentFreq = if (spec.endFrequencyHz != null) {
                spec.startFrequencyHz + (spec.endFrequencyHz!! - spec.startFrequencyHz) * progress
            } else {
                spec.startFrequencyHz
            }

            phase += (currentFreq / spec.sampleRate) * TWO_PI
            if (phase > TWO_PI) phase -= TWO_PI

            val rawSample = when (spec.waveform) {
                Waveform.SINE -> sin(phase)
                Waveform.SQUARE -> if (phase < PI.toFloat()) 1f else -1f
                Waveform.TRIANGLE -> (2f / PI.toFloat()) * kotlin.math.asin(sin(phase))
                Waveform.SAWTOOTH -> (phase / PI.toFloat()) - 1f
                Waveform.NOISE -> (random.nextFloat() * 2f - 1f)
            }

            val filtered = if (spec.lowPassFilter > 0f) {
                (lastFiltered * spec.lowPassFilter) + (rawSample * (1f - spec.lowPassFilter))
            } else {
                rawSample
            }
            lastFiltered = filtered

            val envelopeAmp = spec.envelope.amplitudeAt(t, spec.duration)
            val sampleVal = (filtered * envelopeAmp * spec.volume * 32767f).toInt().coerceIn(-32767, 32767)

            val byteIdx = i * 2
            pcm[byteIdx] = (sampleVal and 0xFF).toByte()
            pcm[byteIdx + 1] = ((sampleVal shr 8) and 0xFF).toByte()
        }

        return AudioClip(
            id = spec.id,
            name = spec.name,
            pcmBytes = pcm,
            sampleRate = spec.sampleRate,
            channels = 1,
            bitsPerSample = 16,
        )
    }

    /**
     * Synthesizes a pure continuous tone of a specified frequency.
     */
    fun tone(id: String, name: String, frequencyHz: Float, durationSeconds: Float, waveform: Waveform = Waveform.SINE): AudioClip {
        return build(id, name) {
            duration = durationSeconds
            this.waveform = waveform
            startFrequencyHz = frequencyHz
            envelope = AdsrEnvelope(attack = 0.01f, decay = 0.05f, sustain = 0.9f, release = 0.04f)
        }
    }

    /**
     * Synthesizes shaped procedural noise.
     */
    fun noise(id: String, name: String, durationSeconds: Float, smoothing: Float = 0.5f): AudioClip {
        return build(id, name) {
            duration = durationSeconds
            waveform = Waveform.NOISE
            lowPassFilter = smoothing
            envelope = AdsrEnvelope(attack = 0.02f, decay = durationSeconds - 0.02f)
        }
    }

    /**
     * Synthesizes a sequential arpeggio of note frequencies.
     */
    fun arpeggio(id: String, name: String, notesHz: List<Float>, noteDurationSeconds: Float = 0.15f): AudioClip {
        val totalDuration = noteDurationSeconds * notesHz.size
        val sampleRate = 44100
        val numSamples = (sampleRate * totalDuration).toInt().coerceAtLeast(1)
        val pcm = ByteArray(numSamples * 2)

        val noteSamples = (sampleRate * noteDurationSeconds).toInt().coerceAtLeast(1)

        for (i in 0 until numSamples) {
            val noteIdx = (i / noteSamples).coerceIn(0, notesHz.size - 1)
            val noteProgress = (i % noteSamples).toFloat() / noteSamples
            val noteEnvelope = (1f - (noteProgress * 0.7f))

            val freq = notesHz[noteIdx]
            val phase = (i * freq / sampleRate) * TWO_PI
            val sample = sin(phase)

            val sampleVal = (sample * noteEnvelope * 22000f).toInt().coerceIn(-32767, 32767)
            val byteIdx = i * 2
            pcm[byteIdx] = (sampleVal and 0xFF).toByte()
            pcm[byteIdx + 1] = ((sampleVal shr 8) and 0xFF).toByte()
        }

        return AudioClip(
            id = id,
            name = name,
            pcmBytes = pcm,
            sampleRate = sampleRate,
            channels = 1,
            bitsPerSample = 16,
        )
    }
}
