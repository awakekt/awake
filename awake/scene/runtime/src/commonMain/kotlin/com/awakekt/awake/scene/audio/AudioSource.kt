/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.audio

import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.audio.SoundHandle

/**
 * ECS component representing a 2D or 3D spatial sound emitter in the scene.
 *
 * @property clip The PCM audio clip to play.
 * @property volume Playback volume multiplier (0.0 to 1.0).
 * @property isSpatial3D Whether distance attenuation and 3D positional panning apply.
 * @property maxDistance Maximum audible distance radius for 3D spatial sounds.
 * @property autoPlay Whether playback starts automatically when the scene is ready.
 * @property loop Whether the sound loops continuously.
 */
data class AudioSource(
    val clip: AudioClip,
    var volume: Float = 1f,
    var isSpatial3D: Boolean = true,
    var maxDistance: Float = 50f,
    var autoPlay: Boolean = true,
    var loop: Boolean = false,
) {
    var playingHandle: SoundHandle? = null
    val isPlaying: Boolean get() = playingHandle?.isPlaying == true
}

/**
 * Marker ECS component attached to the active camera entity receiving 3D audio.
 */
class AudioListener
