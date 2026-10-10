/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.audio

import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.audio.AudioPlayer
import com.awakekt.awake.core.audio.PositionalAudioMath
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.sqrt

/**
 * ECS System managing 2D/3D spatial audio updating and playback.
 *
 * Scans entities for [AudioSource] components, computes 3D spatial attenuation and panning
 * relative to the active [AudioListener] entity, and updates playback.
 *
 * @param audioPlayer The underlying player used to trigger and manage audio playback.
 * @param clips Decoded clips by project path, as [loadAudioClips] reads them. A source whose clip holds
 * no samples, as [AudioSourceBinding] attaches it, plays the clip named here once it's in the map.
 */
class AudioSystem(
    private val audioPlayer: AudioPlayer,
    private val clips: Map<String, AudioClip> = emptyMap(),
) : System {
    /** Sources found holding a placeholder this frame; a field, so swapping allocates nothing once they're done. */
    private val waiting = ArrayList<Pair<Entity, AudioSource>>()

    /**
     * Updates spatial audio emitters and listeners across the simulation world.
     *
     * @param world The ECS simulation world.
     * @param delta Time elapsed in seconds since the previous update frame.
     */
    override fun update(world: World, delta: Float) {
        val listenerEntity = world.query(AudioListener::class, Transform::class).firstOrNull()
        val listenerPos = listenerEntity?.let { world.get<Transform>(it)?.position } ?: Vec3f.ZERO
        val listenerRight = listenerEntity?.let {
            val mat = world.get<Transform>(it)?.worldMatrix
            if (mat != null) Vec3f(mat.m00, mat.m10, mat.m20) else Vec3f.RIGHT
        } ?: Vec3f.RIGHT

        val listener = AudioListenerContext(pos = listenerPos, right = listenerRight)
        if (clips.isNotEmpty()) swapInClips(world)

        world.family<AudioSource>().forEach { entity, source ->
            updateSource(world, entity, source, listener)
        }
    }

    private fun updateSource(
        world: World,
        entity: Entity,
        source: AudioSource,
        listener: AudioListenerContext,
    ) {
        val handle = source.playingHandle
        if (handle != null && !handle.isPlaying) {
            handle.stop()
            source.playingHandle = null
        }

        if (source.isPlaying) {
            updateActiveSource(world, entity, source, listener)
        }

        if (source.canAutoPlay()) {
            source.hasAutoPlayed = true
            triggerPlayback(world, entity, source, listener)
        }
    }

    private fun updateActiveSource(
        world: World,
        entity: Entity,
        source: AudioSource,
        listener: AudioListenerContext,
    ) {
        val active = source.playingHandle ?: return
        if (source.isSpatial3D) {
            val emitterPos = world.get<Transform>(entity)?.position ?: Vec3f.ZERO
            val dx = emitterPos.x - listener.pos.x
            val dy = emitterPos.y - listener.pos.y
            val dz = emitterPos.z - listener.pos.z
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            val attenuation = PositionalAudioMath.computeAttenuation(distance, source.maxDistance) * source.volume
            val pan = PositionalAudioMath.computePan(emitterPos, listener.pos, listener.right)
            active.update(volume = attenuation, pan = pan)
        } else {
            active.update(volume = source.volume, pan = 0f)
        }
    }

    private fun triggerPlayback(
        world: World,
        entity: Entity,
        source: AudioSource,
        listener: AudioListenerContext,
    ) {
        if (source.isSpatial3D) {
            val emitterPos = world.get<Transform>(entity)?.position ?: Vec3f.ZERO
            source.playingHandle = audioPlayer.playSound3D(
                clip = source.clip,
                emitterPos = emitterPos,
                listenerPos = listener.pos,
                listenerRight = listener.right,
                maxDistance = source.maxDistance,
                loop = source.loop,
            )
        } else {
            source.playingHandle = audioPlayer.playSound(
                clip = source.clip,
                volume = source.volume,
                loop = source.loop,
            )
        }
    }

    /** Gives each source still holding a placeholder clip the decoded one, before any of them plays. */
    private fun swapInClips(world: World) {
        world.family<AudioSource>().forEach { entity, source ->
            if (source.clip.pcmBytes.isEmpty() && source.clip.id in clips) waiting += entity to source
        }
        // After the walk: replacing a component while walking its family would move under the walk.
        for (index in waiting.indices) {
            val (entity, source) = waiting[index]
            world.add(entity, source.copy(clip = clips.getValue(source.clip.id)))
        }
        waiting.clear()
    }

    private fun AudioSource.canAutoPlay(): Boolean =
        autoPlay && !hasAutoPlayed && playingHandle == null && clip.pcmBytes.isNotEmpty()
}

private class AudioListenerContext(
    val pos: Vec3f,
    val right: Vec3f,
)
