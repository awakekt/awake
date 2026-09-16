/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

/**
 * Headless fallback audio player for unit tests and platforms without native audio drivers.
 */
open class NoOpAudioPlayer(
    private val defaultPlayingState: Boolean = true,
) : AudioPlayer {
    override var masterVolume: Float = 1.0f
    override var sfxVolume: Float = 1.0f
    override var musicVolume: Float = 0.6f

    override fun playSound(
        clip: AudioClip,
        volume: Float,
        pan: Float,
        loop: Boolean,
    ): SoundHandle? = NoOpSoundHandle(defaultPlayingState)

    override fun playMusic(clip: AudioClip, volume: Float, loop: Boolean) {}

    override fun stopMusic() {}

    override fun dispose() {}
}

/**
 * Headless sound handle for testing.
 */
open class NoOpSoundHandle(
    override var isPlaying: Boolean = true,
) : SoundHandle {
    override fun stop() {
        isPlaying = false
    }
}
