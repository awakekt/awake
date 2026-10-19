/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

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
    private val voicePool = HashMap<String, ArrayList<Clip>>()
    private var isDisposed = false

    override fun playSound(
        clip: AudioClip,
        volume: Float,
        pan: Float,
        loop: Boolean,
    ): SoundHandle? {
        val totalVolume = (masterVolume * sfxVolume * volume).coerceIn(0f, 1f)
        if (isDisposed || totalVolume <= 0.0001f) return null

        return runCatching {
            val jvmClip = acquireClip(clip) ?: return@runCatching null

            // Apply Volume (Decibels)
            applyGain(jvmClip, totalVolume)

            // Apply Stereo Panning
            applyPan(jvmClip, pan)

            if (loop) {
                jvmClip.loop(Clip.LOOP_CONTINUOUSLY)
            } else {
                jvmClip.start()
            }

            object : SoundHandle {
                override val isPlaying: Boolean
                    get() = runCatching { !isDisposed && jvmClip.isOpen && jvmClip.isRunning }.getOrDefault(false)

                override fun stop() {
                    runCatching {
                        if (jvmClip.isRunning) {
                            jvmClip.stop()
                        }
                        jvmClip.framePosition = 0
                    }
                }

                override fun update(volume: Float, pan: Float) {
                    if (isDisposed || !jvmClip.isOpen) return
                    val dynamicVolume = (masterVolume * sfxVolume * volume).coerceIn(0f, 1f)
                    applyGain(jvmClip, dynamicVolume)
                    applyPan(jvmClip, pan)
                }
            }
        }.getOrNull()
    }

    private fun acquireClip(clip: AudioClip): Clip? {
        val pool = voicePool.getOrPut(clip.id) { ArrayList(MAX_VOICES_PER_CLIP) }
        var idle: Clip? = null
        for (i in pool.indices) {
            val candidate = pool[i]
            if (candidate.isOpen && !candidate.isRunning) {
                idle = candidate
                break
            }
        }
        val chosen = when {
            idle != null -> idle
            pool.size < MAX_VOICES_PER_CLIP -> {
                val audioFormat = createAudioFormat(clip)
                val audioStream = AudioInputStream(
                    ByteArrayInputStream(clip.pcmBytes),
                    audioFormat,
                    (clip.pcmBytes.size / audioFormat.frameSize).toLong(),
                )
                AudioSystem.getClip().also {
                    it.open(audioStream)
                    pool.add(it)
                }
            }
            pool.isNotEmpty() && pool[0].isOpen -> {
                val candidate = pool[0]
                if (candidate.isRunning) candidate.stop()
                candidate
            }
            else -> null
        }
        chosen?.framePosition = 0
        return chosen
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
        if (isDisposed) return
        isDisposed = true
        stopMusic()
        for (clips in voicePool.values) {
            for (clip in clips) {
                runCatching {
                    if (clip.isRunning) clip.stop()
                    if (clip.isOpen) clip.close()
                }
            }
        }
        voicePool.clear()
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

private const val MAX_VOICES_PER_CLIP = 4
