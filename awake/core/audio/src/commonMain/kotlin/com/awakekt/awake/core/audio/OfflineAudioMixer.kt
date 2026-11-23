/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Sample-accurate pure-Kotlin software PCM mixer implementing [AudioPlayer] for headless trailer
 * rendering and deterministic audio verification.
 *
 * Feeds every 2D SFX, 3D positional SFX, and music stream into a stereo floating-point mix buffer
 * stepped synchronously with the simulation frame clock via [advance]. Supports arbitrary clip
 * sample-rate resampling, slow-motion SFX pitch shifting, dynamic music ducking under stingers or
 * dialogue, soft-clipping limiting, and 16-bit stereo RIFF/WAVE export via [encodeWav].
 *
 * @property sampleRate Output mix sample rate in Hz (default `48000`).
 */
@Suppress("TooManyFunctions")
class OfflineAudioMixer(
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
) : AudioPlayer {
    init {
        require(sampleRate > 0) { "sampleRate must be positive, got $sampleRate." }
    }

    override var masterVolume: Float = 1f
    override var sfxVolume: Float = 1f
    override var musicVolume: Float = 1f

    private val activeSfx = mutableListOf<MixedVoice>()
    private var musicVoice: MixedVoice? = null
    private var musicTargetVolume: Float = 1f
    private var musicFadeSpeedPerSample: Float = 0f

    // Dynamic music ducking envelope state.
    private var duckGain: Float = 1f
    private var duckTargetGain: Float = 1f
    private var duckHoldSamples: Int = 0
    private var duckAttackRate: Float = 0f
    private var duckReleaseRate: Float = 0f

    // Interleaved stereo float output buffer (left, right, left, right, ...).
    private var buffer = FloatArray(INITIAL_BUFFER_FLOATS)
    private var mixedSamples: Int = 0
    private var fractionalSampleCarry: Double = 0.0

    /** Total number of stereo sample frames mixed so far. */
    val mixedFrameCount: Int get() = mixedSamples

    /** Total duration of mixed audio in seconds. */
    val mixedDurationSeconds: Float get() = mixedSamples.toFloat() / sampleRate

    override fun playSound(
        clip: AudioClip,
        volume: Float,
        pan: Float,
        loop: Boolean,
    ): SoundHandle? {
        if (clip.pcmBytes.isEmpty() || clip.sampleRate <= 0 || clip.channels <= 0) return null
        val voice = MixedVoice(
            clip = clip,
            volume = volume.coerceAtLeast(0f),
            pan = pan.coerceIn(-1f, 1f),
            loop = loop,
            pitch = 1f,
            isMusic = false,
        )
        activeSfx.add(voice)
        return voice
    }

    /**
     * Plays a 2D sound effect with an explicit base [pitch] multiplier (e.g. for pitched impact
     * stingers or director audio cues).
     *
     * @param clip Decoded PCM audio clip to play.
     * @param volume Playback volume multiplier (`>= 0f`).
     * @param pan Stereo pan position in `-1f..1f`.
     * @param pitch Base playback rate multiplier (`> 0f`, default `1f`).
     * @param loop Whether the clip loops until stopped.
     * @return Active [SoundHandle], or `null` if [clip] holds no samples.
     */
    fun playSoundPitched(
        clip: AudioClip,
        volume: Float = 1f,
        pan: Float = 0f,
        pitch: Float = 1f,
        loop: Boolean = false,
    ): SoundHandle? {
        if (clip.pcmBytes.isEmpty() || clip.sampleRate <= 0 || clip.channels <= 0) return null
        val voice = MixedVoice(
            clip = clip,
            volume = volume.coerceAtLeast(0f),
            pan = pan.coerceIn(-1f, 1f),
            loop = loop,
            pitch = pitch.coerceAtLeast(MIN_PITCH),
            isMusic = false,
        )
        activeSfx.add(voice)
        return voice
    }

    override fun playMusic(clip: AudioClip, volume: Float, loop: Boolean) {
        playMusic(clip = clip, volume = volume, loop = loop, startOffsetSeconds = 0f, fadeInSeconds = 0f)
    }

    /**
     * Starts background music playback with optional start seek offset and smooth fade-in.
     *
     * @param clip Music audio clip to play.
     * @param volume Target music volume multiplier (`>= 0f`).
     * @param loop Whether the track repeats after reaching its end.
     * @param startOffsetSeconds Initial seek offset into [clip] in seconds.
     * @param fadeInSeconds Fade-in duration in seconds (`0f` starts immediately at [volume]).
     */
    fun playMusic(
        clip: AudioClip,
        volume: Float = DEFAULT_MUSIC_VOLUME,
        loop: Boolean = true,
        startOffsetSeconds: Float = 0f,
        fadeInSeconds: Float = 0f,
    ) {
        stopMusic()
        if (clip.pcmBytes.isEmpty() || clip.sampleRate <= 0 || clip.channels <= 0) return
        val clampedTarget = volume.coerceAtLeast(0f)
        val initialVolume = if (fadeInSeconds > 0f) 0f else clampedTarget
        val voice = MixedVoice(
            clip = clip,
            volume = initialVolume,
            pan = 0f,
            loop = loop,
            pitch = 1f,
            isMusic = true,
        )
        val startFrame = (startOffsetSeconds.coerceAtLeast(0f) * clip.sampleRate).toDouble()
        voice.seekFrames(startFrame)
        musicVoice = voice
        musicTargetVolume = clampedTarget
        musicFadeSpeedPerSample = if (fadeInSeconds > 0f) {
            clampedTarget / (fadeInSeconds * sampleRate)
        } else {
            0f
        }
    }

    /**
     * Smoothly fades the active music track to [targetVolume] over [fadeSeconds].
     *
     * @param targetVolume Target volume multiplier (`>= 0f`).
     * @param fadeSeconds Duration of the fade in seconds (`<= 0f` applies immediately).
     */
    fun fadeMusicTo(targetVolume: Float, fadeSeconds: Float) {
        val voice = musicVoice ?: return
        val clamped = targetVolume.coerceAtLeast(0f)
        musicTargetVolume = clamped
        if (fadeSeconds <= 0f) {
            voice.volume = clamped
            musicFadeSpeedPerSample = 0f
        } else {
            musicFadeSpeedPerSample = abs(clamped - voice.volume) / (fadeSeconds * sampleRate)
        }
    }

    /**
     * Triggers a smooth ducking dip on the music bus (for example when a stinger, explosion, or
     * voice line hits).
     *
     * @param gainLinear Duck gain multiplier applied to music during hold (e.g. `0.35f` for ~-9 dB).
     * @param holdSeconds Duration to hold the ducked level before releasing.
     * @param attackSeconds Ramp-down duration into ducking (default `0.04f`).
     * @param releaseSeconds Ramp-up duration back to full music gain (default `0.35f`).
     */
    fun triggerDuck(
        gainLinear: Float,
        holdSeconds: Float,
        attackSeconds: Float = DEFAULT_DUCK_ATTACK_SECONDS,
        releaseSeconds: Float = DEFAULT_DUCK_RELEASE_SECONDS,
    ) {
        val target = gainLinear.coerceIn(0f, 1f)
        if (target < duckTargetGain || duckHoldSamples == 0) {
            duckTargetGain = target
        }
        val hold = (holdSeconds.coerceAtLeast(0f) * sampleRate).toInt()
        if (hold > duckHoldSamples) {
            duckHoldSamples = hold
        }
        duckAttackRate = if (attackSeconds > 0f) 1f / (attackSeconds * sampleRate) else 1f
        duckReleaseRate = if (releaseSeconds > 0f) 1f / (releaseSeconds * sampleRate) else 1f
    }

    override fun stopMusic() {
        musicVoice?.stop()
        musicVoice = null
        musicFadeSpeedPerSample = 0f
    }

    /**
     * Mixes [deltaSeconds] of audio into the output buffer, advancing all active voices and
     * envelopes deterministically.
     *
     * Fractional samples are carried across calls so `60` fps at `44100` or `48000` Hz never drifts
     * out of sync with video frames over long trailers.
     *
     * @param deltaSeconds Frame duration in real trailer seconds (`> 0f`).
     * @param sfxPitchScale Global playback rate multiplier applied to SFX voices (e.g. `< 1f`
     *   during slow-motion speed ramps; music stays at `1f` so musical tempo remains locked).
     */
    fun advance(deltaSeconds: Float, sfxPitchScale: Float = 1f) {
        if (deltaSeconds <= 0f || !deltaSeconds.isFinite()) return
        val exactSamples = deltaSeconds.toDouble() * sampleRate + fractionalSampleCarry
        val sampleCount = exactSamples.toInt()
        fractionalSampleCarry = exactSamples - sampleCount
        if (sampleCount <= 0) return

        ensureCapacity(mixedSamples + sampleCount)
        val clampedSfxPitch = sfxPitchScale.coerceAtLeast(MIN_PITCH)

        for (i in 0 until sampleCount) {
            var left = 0f
            var right = 0f

            // 1. Mix active SFX voices.
            var voiceIndex = 0
            while (voiceIndex < activeSfx.size) {
                val voice = activeSfx[voiceIndex]
                if (!voice.isPlaying) {
                    activeSfx.removeAt(voiceIndex)
                    continue
                }
                val gain = voice.volume * sfxVolume
                if (gain > 0f) {
                    val panAngle = (voice.pan + 1f) * QUARTER_PI
                    val leftGain = cos(panAngle) * gain
                    val rightGain = sin(panAngle) * gain
                    val (sL, sR) = voice.sampleStereo()
                    left += sL * leftGain
                    right += sR * rightGain
                }
                voice.step(sampleRate, clampedSfxPitch)
                if (!voice.isPlaying) {
                    activeSfx.removeAt(voiceIndex)
                } else {
                    voiceIndex++
                }
            }

            // 2. Mix active music voice with fade + ducking envelope.
            val music = musicVoice
            if (music != null && music.isPlaying) {
                stepMusicFade(music)
                val effectiveMusicGain = music.volume * musicVolume * duckGain
                if (effectiveMusicGain > 0f) {
                    val (mL, mR) = music.sampleStereo()
                    left += mL * effectiveMusicGain
                    right += mR * effectiveMusicGain
                }
                music.step(sampleRate, 1f)
                if (!music.isPlaying) {
                    musicVoice = null
                }
            }
            stepDuckEnvelope()

            // 3. Apply master gain + gentle soft-clip limiter.
            val outIndex = (mixedSamples + i) * STEREO_CHANNELS
            buffer[outIndex] = softClip(left * masterVolume)
            buffer[outIndex + 1] = softClip(right * masterVolume)
        }
        mixedSamples += sampleCount
    }

    /** Peak absolute sample amplitude across all mixed audio (`0f..1f`). */
    fun peakAmplitude(): Float {
        var peak = 0f
        val count = mixedSamples * STEREO_CHANNELS
        for (i in 0 until count) {
            val v = abs(buffer[i])
            if (v > peak) peak = v
        }
        return peak
    }

    /** Root-mean-square energy across all mixed stereo samples (`0f..1f`). */
    fun rmsEnergy(): Float {
        val count = mixedSamples * STEREO_CHANNELS
        if (count == 0) return 0f
        var sumSq = 0.0
        for (i in 0 until count) {
            val v = buffer[i].toDouble()
            sumSq += v * v
        }
        return sqrt(sumSq / count).toFloat()
    }

    /**
     * Encodes the entire mixed stereo timeline as a standard uncompressed 16-bit PCM RIFF/WAVE
     * byte array, ready to write directly to `.wav` or decode back with [WavDecoder].
     *
     * @return Complete RIFF/WAVE file bytes.
     */
    fun encodeWav(): ByteArray {
        val dataSize = mixedSamples * STEREO_CHANNELS * BYTES_PER_SAMPLE_16
        val riffSize = WAV_HEADER_WITHOUT_RIFF_SIZE + dataSize
        val out = ByteArray(WAV_HEADER_SIZE + dataSize)

        // "RIFF" chunk descriptor.
        writeAscii(out, 0, "RIFF")
        writeIntLe(out, 4, riffSize)
        writeAscii(out, 8, "WAVE")

        // "fmt " sub-chunk.
        writeAscii(out, 12, "fmt ")
        writeIntLe(out, 16, PCM_FMT_CHUNK_SIZE)
        writeShortLe(out, 20, PCM_AUDIO_FORMAT)
        writeShortLe(out, 22, STEREO_CHANNELS)
        writeIntLe(out, 24, sampleRate)
        val byteRate = sampleRate * STEREO_CHANNELS * BYTES_PER_SAMPLE_16
        writeIntLe(out, 28, byteRate)
        val blockAlign = STEREO_CHANNELS * BYTES_PER_SAMPLE_16
        writeShortLe(out, 32, blockAlign)
        writeShortLe(out, 34, BITS_PER_SAMPLE_16)

        // "data" sub-chunk.
        writeAscii(out, 36, "data")
        writeIntLe(out, 40, dataSize)

        var bytePos = WAV_HEADER_SIZE
        val totalFloats = mixedSamples * STEREO_CHANNELS
        for (i in 0 until totalFloats) {
            val sample = (buffer[i].coerceIn(-1f, 1f) * PCM_16_MAX).toInt().coerceIn(-PCM_16_MAX, PCM_16_MAX)
            out[bytePos++] = (sample and BYTE_MASK).toByte()
            out[bytePos++] = ((sample shr BITS_PER_BYTE) and BYTE_MASK).toByte()
        }
        return out
    }

    override fun dispose() {
        activeSfx.forEach(MixedVoice::stop)
        activeSfx.clear()
        stopMusic()
    }

    private fun stepMusicFade(music: MixedVoice) {
        if (musicFadeSpeedPerSample <= 0f) return
        val diff = musicTargetVolume - music.volume
        if (abs(diff) <= musicFadeSpeedPerSample) {
            music.volume = musicTargetVolume
            musicFadeSpeedPerSample = 0f
        } else {
            music.volume += if (diff > 0f) musicFadeSpeedPerSample else -musicFadeSpeedPerSample
        }
    }

    private fun stepDuckEnvelope() {
        if (duckHoldSamples > 0) {
            if (duckGain > duckTargetGain) {
                duckGain = (duckGain - duckAttackRate).coerceAtLeast(duckTargetGain)
            } else {
                duckHoldSamples--
            }
        } else if (duckGain < 1f) {
            duckTargetGain = 1f
            duckGain = (duckGain + duckReleaseRate).coerceAtMost(1f)
        }
    }

    private fun ensureCapacity(requiredFrames: Int) {
        val requiredFloats = requiredFrames * STEREO_CHANNELS
        if (requiredFloats <= buffer.size) return
        var nextSize = buffer.size.coerceAtLeast(INITIAL_BUFFER_FLOATS)
        while (nextSize < requiredFloats) {
            nextSize *= 2
        }
        buffer = buffer.copyOf(nextSize)
    }

    private fun softClip(sample: Float): Float {
        val absVal = abs(sample)
        if (absVal <= SOFT_CLIP_KNEE) return sample
        return tanh(sample.toDouble()).toFloat()
    }

    /** Internal live voice tracking fractional source frame position for linear resampling. */
    private class MixedVoice(
        private val clip: AudioClip,
        var volume: Float,
        var pan: Float,
        private val loop: Boolean,
        private val pitch: Float,
        private val isMusic: Boolean,
    ) : SoundHandle {
        private val frameCount: Int = run {
            val bytesPerFrame = (clip.bitsPerSample / BITS_PER_BYTE).coerceAtLeast(1) * clip.channels.coerceAtLeast(1)
            clip.pcmBytes.size / bytesPerFrame
        }
        private var framePos: Double = 0.0
        private var stopped: Boolean = frameCount <= 0

        override val isPlaying: Boolean get() = !stopped

        override fun stop() {
            stopped = true
        }

        override fun update(volume: Float, pan: Float) {
            this.volume = volume.coerceAtLeast(0f)
            this.pan = pan.coerceIn(-1f, 1f)
        }

        fun seekFrames(targetFrame: Double) {
            if (frameCount <= 0) {
                stopped = true
                return
            }
            framePos = if (loop) {
                targetFrame.mod(frameCount.toDouble())
            } else if (targetFrame >= frameCount) {
                stopped = true
                frameCount.toDouble()
            } else {
                targetFrame.coerceAtLeast(0.0)
            }
        }

        fun sampleStereo(): Pair<Float, Float> {
            val idx0 = framePos.toInt().coerceIn(0, frameCount - 1)
            val idx1 = if (idx0 + 1 < frameCount) {
                idx0 + 1
            } else if (loop) {
                0
            } else {
                idx0
            }
            val frac = (framePos - idx0).toFloat().coerceIn(0f, 1f)

            val l0 = readSample(idx0, 0)
            val l1 = readSample(idx1, 0)
            val left = l0 + (l1 - l0) * frac

            val right = if (clip.channels >= STEREO_CHANNELS) {
                val r0 = readSample(idx0, 1)
                val r1 = readSample(idx1, 1)
                r0 + (r1 - r0) * frac
            } else {
                left
            }
            return left to right
        }

        fun step(outputSampleRate: Int, globalPitchScale: Float) {
            val rate = (clip.sampleRate.toDouble() / outputSampleRate.toDouble()) * pitch * (if (isMusic) 1.0 else globalPitchScale.toDouble())
            framePos += rate
            if (framePos >= frameCount) {
                if (loop && frameCount > 0) {
                    framePos %= frameCount.toDouble()
                } else {
                    stopped = true
                }
            }
        }

        private fun readSample(frameIndex: Int, channel: Int): Float {
            val byteOffset = (frameIndex * clip.channels + channel) * BYTES_PER_SAMPLE_16
            return if (clip.bitsPerSample != BITS_PER_SAMPLE_16 || byteOffset + 1 >= clip.pcmBytes.size) {
                0f
            } else {
                val lo = clip.pcmBytes[byteOffset].toInt() and BYTE_MASK
                val hi = clip.pcmBytes[byteOffset + 1].toInt()
                val signed16 = (hi shl BITS_PER_BYTE) or lo
                signed16 / PCM_16_NORM
            }
        }
    }

    /** Default constants for [OfflineAudioMixer]. */
    companion object {
        /** Default studio/video mix sample rate in Hz (48 kHz standard for MP4/AAC). */
        const val DEFAULT_SAMPLE_RATE: Int = 48_000

        private const val STEREO_CHANNELS = 2
        private const val BITS_PER_SAMPLE_16 = 16
        private const val BYTES_PER_SAMPLE_16 = 2
        private const val BITS_PER_BYTE = 8
        private const val BYTE_MASK = 0xFF
        private const val PCM_16_MAX = 32767
        private const val PCM_16_NORM = 32768f
        private const val INITIAL_BUFFER_FLOATS = 48_000 * 2
        private const val QUARTER_PI = (PI / 4.0).toFloat()
        private const val SOFT_CLIP_KNEE = 0.9f
        private const val MIN_PITCH = 0.05f
        private const val DEFAULT_MUSIC_VOLUME = 0.6f
        private const val DEFAULT_DUCK_ATTACK_SECONDS = 0.04f
        private const val DEFAULT_DUCK_RELEASE_SECONDS = 0.35f
        private const val WAV_HEADER_SIZE = 44
        private const val WAV_HEADER_WITHOUT_RIFF_SIZE = 36
        private const val PCM_FMT_CHUNK_SIZE = 16
        private const val PCM_AUDIO_FORMAT = 1
    }
}

private fun writeAscii(buffer: ByteArray, offset: Int, text: String) {
    for (i in text.indices) {
        buffer[offset + i] = text[i].code.toByte()
    }
}

private fun writeShortLe(buffer: ByteArray, offset: Int, value: Int) {
    buffer[offset] = (value and 0xFF).toByte()
    buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
}

private fun writeIntLe(buffer: ByteArray, offset: Int, value: Int) {
    buffer[offset] = (value and 0xFF).toByte()
    buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    buffer[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    buffer[offset + 3] = ((value ushr 24) and 0xFF).toByte()
}
