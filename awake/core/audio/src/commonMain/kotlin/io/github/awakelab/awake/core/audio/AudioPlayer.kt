/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.audio

import io.github.awakelab.awake.core.math.Vec3f
import kotlin.math.sqrt

/** Handle to an actively playing sound instance. */
interface SoundHandle {
    val isPlaying: Boolean
    fun stop()
}

/**
 * 3D spatial calculations for sound attenuation and stereo panning.
 */
object PositionalAudioMath {

    /**
     * Calculates inverse distance volume attenuation.
     */
    fun computeAttenuation(distance: Float, maxDistance: Float, rollOff: Float = 1f): Float {
        if (distance <= 0f) return 1f
        if (distance >= maxDistance) return 0f
        return (1f / (1f + (distance / maxDistance) * rollOff * 2f)).coerceIn(0f, 1f)
    }

    /**
     * Calculates stereo panning [-1.0f (left) to +1.0f (right)] from listener's perspective.
     */
    fun computePan(emitterPos: Vec3f, listenerPos: Vec3f, listenerRight: Vec3f): Float {
        val dx = emitterPos.x - listenerPos.x
        val dy = emitterPos.y - listenerPos.y
        val dz = emitterPos.z - listenerPos.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist <= 0.0001f) return 0f

        val dirX = dx / dist
        val dirY = dy / dist
        val dirZ = dz / dist

        val dot = (dirX * listenerRight.x) + (dirY * listenerRight.y) + (dirZ * listenerRight.z)
        return dot.coerceIn(-1f, 1f)
    }
}

/**
 * Multiplatform audio playback manager contract for 2D/3D SFX and background music.
 */
interface AudioPlayer {
    var masterVolume: Float
    var sfxVolume: Float
    var musicVolume: Float

    fun playSound(clip: AudioClip, volume: Float = 1f, pan: Float = 0f): SoundHandle?

    fun playSound3D(
        clip: AudioClip,
        emitterPos: Vec3f,
        listenerPos: Vec3f,
        listenerRight: Vec3f,
        maxDistance: Float = 50f,
    ): SoundHandle? {
        val dx = emitterPos.x - listenerPos.x
        val dy = emitterPos.y - listenerPos.y
        val dz = emitterPos.z - listenerPos.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        val attenuation = PositionalAudioMath.computeAttenuation(distance, maxDistance)
        if (attenuation <= 0.001f) return null

        val pan = PositionalAudioMath.computePan(emitterPos, listenerPos, listenerRight)
        return playSound(clip, volume = attenuation, pan = pan)
    }

    fun playMusic(clip: AudioClip, volume: Float = 0.6f, loop: Boolean = true)
    fun stopMusic()
    fun dispose()
}

/**
 * Factory providing the active platform [AudioPlayer].
 */
object AudioPlayerFactory {
    var provider: () -> AudioPlayer = { NoOpAudioPlayer() }

    fun create(): AudioPlayer = provider()
}
