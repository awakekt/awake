/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.authoring.dsl

import io.github.awakelab.awake.core.audio.AudioClip
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.scene.audio.AudioListener
import io.github.awakelab.awake.scene.audio.AudioSource
import io.github.awakelab.awake.scene.authoring.SceneAppDsl

/**
 * Attaches an [AudioSource] component to this entity.
 *
 * @param clip The PCM audio clip to play.
 * @param volume Playback volume (0.0 to 1.0).
 * @param isSpatial3D Whether 3D distance attenuation and panning apply.
 * @param maxDistance Maximum audible radius for 3D spatial sounds.
 * @param autoPlay Whether playback starts automatically.
 * @param loop Whether the sound loops continuously.
 */
fun EntityScope.audioSource(
    clip: AudioClip,
    volume: Float = 1f,
    isSpatial3D: Boolean = true,
    maxDistance: Float = 50f,
    autoPlay: Boolean = true,
    loop: Boolean = false,
) {
    with(
        AudioSource(
            clip = clip,
            volume = volume,
            isSpatial3D = isSpatial3D,
            maxDistance = maxDistance,
            autoPlay = autoPlay,
            loop = loop,
        ),
    )
}

/**
 * Attaches an [AudioListener] component to this entity (usually the primary camera entity).
 */
fun EntityScope.audioListener() {
    with(AudioListener())
}

/**
 * Spawns a 3D audio emitter entity in a [SceneBuilder].
 *
 * @param clip The PCM audio clip.
 * @param name Optional entity name ("audio-source" by default).
 * @param volume Playback volume.
 * @param maxDistance Maximum 3D audible radius.
 * @return The spawned audio emitter [Entity].
 */
fun SceneBuilder.sound(
    clip: AudioClip,
    name: String = "audio-source",
    volume: Float = 1f,
    maxDistance: Float = 50f,
): Entity = entity(name) {
    audioSource(clip, volume = volume, maxDistance = maxDistance)
}

/**
 * Spawns a 3D audio emitter entity in a [SceneAppDsl].
 */
fun SceneAppDsl.sound(
    clip: AudioClip,
    name: String = "audio-source",
    volume: Float = 1f,
    maxDistance: Float = 50f,
) {
    entity(name) {
        audioSource(clip, volume = volume, maxDistance = maxDistance)
    }
}
