/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.audio

import com.awakekt.awake.core.audio.AudioPlayer
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform

/**
 * ECS System managing 2D/3D spatial audio updating and playback.
 *
 * Scans entities for [AudioSource] components, computes 3D spatial attenuation and panning
 * relative to the active [AudioListener] entity, and updates playback.
 */
class AudioSystem(
    private val audioPlayer: AudioPlayer,
) : System {

    override fun update(world: World, delta: Float) {
        val listenerEntity = world.query(AudioListener::class, Transform::class).firstOrNull()
        val listenerPos = listenerEntity?.let { world.get<Transform>(it)?.position } ?: Vec3f.ZERO
        val listenerRight = listenerEntity?.let {
            val mat = world.get<Transform>(it)?.worldMatrix
            if (mat != null) Vec3f(mat.m00, mat.m10, mat.m20) else Vec3f.RIGHT
        } ?: Vec3f.RIGHT

        world.family<AudioSource>().forEach { entity, source ->
            if (source.autoPlay && !source.isPlaying && source.playingHandle == null) {
                if (source.isSpatial3D) {
                    val transform = world.get<Transform>(entity)
                    val emitterPos = transform?.position ?: Vec3f.ZERO
                    source.playingHandle = audioPlayer.playSound3D(
                        clip = source.clip,
                        emitterPos = emitterPos,
                        listenerPos = listenerPos,
                        listenerRight = listenerRight,
                        maxDistance = source.maxDistance,
                    )
                } else {
                    source.playingHandle = audioPlayer.playSound(source.clip, volume = source.volume)
                }
            }
        }
    }
}
