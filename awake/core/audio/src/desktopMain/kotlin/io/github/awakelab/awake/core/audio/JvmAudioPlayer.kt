/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.audio

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/**
 * Desktop JVM low-latency audio player built directly on standard Java Sound (`javax.sound.sampled`).
 * Zero external C++ or JNI dependencies.
 */
class JvmAudioPlayer : AudioPlayer {
    override var masterVolume: Float = 1.0f
    override var sfxVolume: Float = 1.0f
    override var musicVolume: Float = 0.6f

    private var activeMusicClip: Clip? = null

    override fun playSound(clip: AudioClip, volume: Float, pan: Float): SoundHandle? {
        val totalVolume = (masterVolume * sfxVolume * volume).coerceIn(0f, 1f)
        if (totalVolume <= 0.0001f) return null

        return runCatching {
            val audioFormat = createAudioFormat(clip)
            val audioStream = AudioInputStream(
                ByteArrayInputStream(clip.pcmBytes),
                audioFormat,
                (clip.pcmBytes.size / audioFormat.frameSize).toLong(),
            )
            val jvmClip = AudioSystem.getClip()
            jvmClip.open(audioStream)

            // Apply Volume (Decibels)
            applyGain(jvmClip, totalVolume)

            // Apply Stereo Panning
            applyPan(jvmClip, pan)

            jvmClip.start()

            object : SoundHandle {
                override val isPlaying: Boolean
                    get() = jvmClip.isRunning

                override fun stop() {
                    if (jvmClip.isRunning) {
                        jvmClip.stop()
                    }
                    jvmClip.close()
                }
            }
        }.getOrNull()
    }

    override fun playMusic(clip: AudioClip, volume: Float, loop: Boolean) {
        stopMusic()
        val totalVolume = (masterVolume * musicVolume * volume).coerceIn(0f, 1f)
        if (totalVolume <= 0.0001f) return

        runCatching {
            val audioFormat = createAudioFormat(clip)
            val audioStream = AudioInputStream(
                ByteArrayInputStream(clip.pcmBytes),
                audioFormat,
                (clip.pcmBytes.size / audioFormat.frameSize).toLong(),
            )
            val jvmClip = AudioSystem.getClip()
            jvmClip.open(audioStream)
            applyGain(jvmClip, totalVolume)

            if (loop) {
                jvmClip.loop(Clip.LOOP_CONTINUOUSLY)
            } else {
                jvmClip.start()
            }
            activeMusicClip = jvmClip
        }
    }

    override fun stopMusic() {
        activeMusicClip?.let {
            if (it.isRunning) it.stop()
            it.close()
        }
        activeMusicClip = null
    }

    override fun dispose() {
        stopMusic()
    }

    private fun createAudioFormat(clip: AudioClip): AudioFormat {
        val frameSize = (clip.bitsPerSample / 8) * clip.channels
        return AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            clip.sampleRate.toFloat(),
            clip.bitsPerSample,
            clip.channels,
            frameSize,
            clip.sampleRate.toFloat(),
            false, // little-endian
        )
    }

    private fun applyGain(clip: Clip, linearVolume: Float) {
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            val gainControl = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val dB = (20.0 * log10(linearVolume.coerceAtLeast(0.0001f).toDouble())).toFloat()
            gainControl.value = dB.coerceIn(gainControl.minimum, gainControl.maximum)
        }
    }

    private fun applyPan(clip: Clip, pan: Float) {
        if (clip.isControlSupported(FloatControl.Type.PAN)) {
            val panControl = clip.getControl(FloatControl.Type.PAN) as FloatControl
            panControl.value = pan.coerceIn(panControl.minimum, panControl.maximum)
        }
    }
}
