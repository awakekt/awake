/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import com.awakekt.awake.core.math.Vec3f
import kotlin.math.sqrt

/**
 * Handle to an actively playing sound instance.
 */
interface SoundHandle {
    /** Whether the underlying audio source is actively playing. */
    val isPlaying: Boolean

    /** Immediately halts playback and releases playback resources. */
    fun stop()

    /**
     * Dynamically updates volume and stereo panning of the playing instance.
     *
     * @param volume Relative playback volume factor between 0.0 and 1.0.
     * @param pan Stereo panning position between -1.0 (hard left) and +1.0 (hard right).
     */
    fun update(volume: Float = 1f, pan: Float = 0f) {}
}

/**
 * 3D spatial calculations for sound attenuation and stereo panning.
 */
object PositionalAudioMath {

    /**
     * Calculates inverse distance volume attenuation.
     *
     * @param distance Distance between audio emitter and listener in world units.
     * @param maxDistance Maximum audible distance beyond which volume attenuates to zero.
     * @param rollOff Attenuation curve steepness factor.
     * @return Attenuation gain multiplier clamped between 0.0 and 1.0.
     */
    fun computeAttenuation(distance: Float, maxDistance: Float, rollOff: Float = 1f): Float {
        if (distance <= 0f) return 1f
        if (distance >= maxDistance) return 0f
        return (1f / (1f + (distance / maxDistance) * rollOff * 2f)).coerceIn(0f, 1f)
    }

    /**
     * Calculates stereo panning [-1.0f (left) to +1.0f (right)] from listener's perspective.
     *
     * @param emitterPos Position of the audio emitter in 3D world space.
     * @param listenerPos Position of the listener in 3D world space.
     * @param listenerRight Normalized right-vector of the listener orientation.
     * @return Stereo pan factor between -1.0 (left) and 1.0 (right).
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
    /** Master volume multiplier affecting all sound effects and music. */
    var masterVolume: Float

    /** Volume multiplier affecting all 2D and 3D sound effects. */
    var sfxVolume: Float

    /** Volume multiplier affecting background music playback. */
    var musicVolume: Float

    /**
     * Plays a 2D sound effect [clip].
     *
     * @param clip The audio clip to play.
     * @param volume Playback volume multiplier.
     * @param pan Stereo pan factor between -1.0 and +1.0.
     * @param loop Whether the sound effect should repeat continuously until stopped.
     * @return Active [SoundHandle] for playback control, or `null` if playback could not be started.
     */
    fun playSound(
        clip: AudioClip,
        volume: Float = 1f,
        pan: Float = 0f,
        loop: Boolean = false,
    ): SoundHandle?

    /**
     * Plays a 3D positional sound effect [clip] with distance attenuation and stereo panning.
     *
     * @param clip The audio clip to play.
     * @param emitterPos Position of the emitter in 3D world space.
     * @param listenerPos Position of the listener in 3D world space.
     * @param listenerRight Normalized right-direction vector of the listener.
     * @param maxDistance Maximum distance in world units where sound is audible.
     * @param loop Whether the sound should repeat continuously until stopped.
     * @return Active [SoundHandle] for playback control, or `null` if attenuated below threshold or failed.
     */
    fun playSound3D(
        clip: AudioClip,
        emitterPos: Vec3f,
        listenerPos: Vec3f,
        listenerRight: Vec3f,
        maxDistance: Float = 50f,
        loop: Boolean = false,
    ): SoundHandle? {
        val dx = emitterPos.x - listenerPos.x
        val dy = emitterPos.y - listenerPos.y
        val dz = emitterPos.z - listenerPos.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        val attenuation = PositionalAudioMath.computeAttenuation(distance, maxDistance)
        if (attenuation <= 0.001f) return null

        val pan = PositionalAudioMath.computePan(emitterPos, listenerPos, listenerRight)
        return playSound(clip, volume = attenuation, pan = pan, loop = loop)
    }

    /**
     * Starts playing [clip] as background music, replacing any currently playing music.
     *
     * @param clip The audio clip to play as background music.
     * @param volume Music playback volume multiplier.
     * @param loop Whether music repeats continuously upon reaching duration.
     */
    fun playMusic(clip: AudioClip, volume: Float = 0.6f, loop: Boolean = true)

    /** Stops current background music playback and closes music streams. */
    fun stopMusic()

    /** Disposes audio playback resources and stops all active sounds. */
    fun dispose()
}

/**
 * Factory providing the active platform [AudioPlayer].
 */
object AudioPlayerFactory {
    /** Factory provider lambda returning an [AudioPlayer] instance. */
    var provider: () -> AudioPlayer = { NoOpAudioPlayer() }

    /**
     * Creates or provides an [AudioPlayer] instance via [provider].
     *
     * @return The configured [AudioPlayer] implementation.
     */
    fun create(): AudioPlayer = provider()
}
