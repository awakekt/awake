/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Basic waveform generator types for sound synthesis.
 */
enum class Waveform {
    /** Pure sinusoidal oscillation. */
    SINE,
    /** Square wave alternating between positive and negative extrema. */
    SQUARE,
    /** Linear rising and falling triangular wave. */
    TRIANGLE,
    /** Linear ramp waveform with rich harmonics. */
    SAWTOOTH,
    /** Uniform pseudo-random noise. */
    NOISE,
}

/**
 * Attack-Decay-Sustain-Release (ADSR) volume envelope.
 *
 * @property attack Duration of the attack phase in seconds.
 * @property decay Duration of the decay phase in seconds.
 * @property sustain Sustained amplitude level between 0 and 1.
 * @property release Duration of the release phase in seconds.
 */
data class AdsrEnvelope(
    val attack: Float = 0.05f,
    val decay: Float = 0.15f,
    val sustain: Float = 0.0f,
    val release: Float = 0.0f,
) {
    /**
     * Calculates the normalized envelope amplitude at the given elapsed time.
     *
     * @param timeSeconds Elapsed time in seconds.
     * @param totalDuration Total sound duration in seconds.
     * @return Normalized amplitude scaling factor between 0.0 and 1.0.
     */
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
 *
 * @property id Unique identifier for the synthesized audio clip.
 * @property name Human-readable name for the audio clip.
 */
class SoundSpecBuilder(val id: String, val name: String) {
    /** Duration of the generated sound in seconds. */
    var duration: Float = 0.2f
    /** Sampling rate in samples per second (Hz). */
    var sampleRate: Int = 44100
    /** Base waveform type to synthesize. */
    var waveform: Waveform = Waveform.SINE
    /** Starting oscillation frequency in Hz. */
    var startFrequencyHz: Float = 440f
    /** Target ending frequency in Hz for pitch sweeps, or null for constant frequency. */
    var endFrequencyHz: Float? = null
    /** ADSR amplitude envelope to apply. */
    var envelope: AdsrEnvelope = AdsrEnvelope()
    /** Low-pass filtering factor between 0.0 (unfiltered) and 1.0 (heavily filtered). */
    var lowPassFilter: Float = 0f
    /** Output volume multiplier between 0.0 and 1.0. */
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
     *
     * @param id Unique identifier for the audio clip.
     * @param name Human-readable clip name.
     * @param block Configuration block initializing sound parameters.
     * @return Synthesized 16-bit PCM [AudioClip].
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
     *
     * @param id Unique identifier for the audio clip.
     * @param name Human-readable clip name.
     * @param frequencyHz Oscillation frequency in Hz.
     * @param durationSeconds Total sound duration in seconds.
     * @param waveform Waveform shape to synthesize.
     * @return Synthesized tone [AudioClip].
     */
    fun tone(id: String, name: String, frequencyHz: Float, durationSeconds: Float, waveform: Waveform = Waveform.SINE): AudioClip = build(id, name) {
        duration = durationSeconds
        this.waveform = waveform
        startFrequencyHz = frequencyHz
        envelope = AdsrEnvelope(attack = 0.01f, decay = 0.05f, sustain = 0.9f, release = 0.04f)
    }

    /**
     * Synthesizes shaped procedural noise.
     *
     * @param id Unique identifier for the audio clip.
     * @param name Human-readable clip name.
     * @param durationSeconds Total sound duration in seconds.
     * @param smoothing Low-pass smoothing factor applied to raw noise samples.
     * @return Synthesized noise [AudioClip].
     */
    fun noise(id: String, name: String, durationSeconds: Float, smoothing: Float = 0.5f): AudioClip = build(id, name) {
        duration = durationSeconds
        waveform = Waveform.NOISE
        lowPassFilter = smoothing
        envelope = AdsrEnvelope(attack = 0.02f, decay = durationSeconds - 0.02f)
    }

    /**
     * Synthesizes a sequential arpeggio of note frequencies.
     *
     * @param id Unique identifier for the audio clip.
     * @param name Human-readable clip name.
     * @param notesHz Frequencies in Hz for each sequential note.
     * @param noteDurationSeconds Duration in seconds for each note in the sequence.
     * @return Synthesized arpeggio [AudioClip].
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
