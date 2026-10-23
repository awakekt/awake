/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.audio

import com.awakekt.awake.core.audio.SoundHandle
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/**
 * One-shot audio cue request queued on an [AudioCueQueue] component.
 *
 * @property path Logical clip identifier or project asset path to play.
 * @property volume Playback volume multiplier (`0.0` to `1.0`).
 * @property pitch Playback pitch multiplier (`1.0` for standard speed).
 * @property maxDistance Maximum audible 3D distance radius; `0.0` or negative plays as 2D audio.
 */
data class OneShotAudioCue(
    val path: String,
    val volume: Float = 1f,
    val pitch: Float = 1f,
    val maxDistance: Float = 0f,
)

/**
 * Per-entity ECS component holding pending one-shot audio cues ([pending]) and an optional active
 * hold/channeling loop ([activeHoldSound]).
 *
 * Gameplay and animation systems enqueue cues via [emit] or [World.emitAudioCue] without holding a
 * direct reference to an [com.awakekt.awake.core.audio.AudioPlayer], and [AudioSystem] (or a custom
 * audio system) drains [pending] on the audio tick.
 */
class AudioCueQueue {
    /** FIFO queue of pending one-shot audio cues awaiting playback. */
    val pending: ArrayDeque<OneShotAudioCue> = ArrayDeque()

    /** Active looping sound handle started during a held action, stopped on release or cancellation. */
    var activeHoldSound: SoundHandle? = null

    /**
     * Enqueues a one-shot audio cue for [path].
     *
     * @param path Logical clip identifier or project asset path to play.
     * @param volume Playback volume multiplier (`0.0` to `1.0`).
     * @param pitch Playback pitch multiplier (`1.0` for standard speed).
     * @param maxDistance Maximum audible 3D distance radius; `0.0` or negative plays as 2D audio.
     */
    fun emit(
        path: String,
        volume: Float = 1f,
        pitch: Float = 1f,
        maxDistance: Float = 0f,
    ) {
        pending.addLast(
            OneShotAudioCue(
                path = path,
                volume = volume,
                pitch = pitch,
                maxDistance = maxDistance,
            ),
        )
    }

    /**
     * Enqueues a [cue] if an identical cue is not already pending.
     *
     * @param cue The one-shot audio cue to enqueue.
     */
    fun emitDistinct(cue: OneShotAudioCue) {
        if (cue !in pending) {
            pending.addLast(cue)
        }
    }

    /** Stops and clears [activeHoldSound] if one is currently playing. */
    fun stopHoldSound() {
        activeHoldSound?.stop()
        activeHoldSound = null
    }
}

/**
 * Ensures [entity] has an [AudioCueQueue] component and returns it.
 *
 * @param entity Target ECS entity.
 * @return The existing or newly attached [AudioCueQueue].
 */
fun World.ensureAudioCueQueue(entity: Entity): AudioCueQueue =
    get<AudioCueQueue>(entity) ?: AudioCueQueue().also { add(entity, it) }

/**
 * Enqueues a one-shot audio cue on [entity]'s [AudioCueQueue].
 *
 * @param entity Target ECS entity.
 * @param path Logical clip identifier or project asset path to play.
 * @param volume Playback volume multiplier (`0.0` to `1.0`).
 * @param pitch Playback pitch multiplier (`1.0` for standard speed).
 * @param maxDistance Maximum audible 3D distance radius; `0.0` or negative plays as 2D audio.
 */
fun World.emitAudioCue(
    entity: Entity,
    path: String,
    volume: Float = 1f,
    pitch: Float = 1f,
    maxDistance: Float = 0f,
) {
    ensureAudioCueQueue(entity).emit(
        path = path,
        volume = volume,
        pitch = pitch,
        maxDistance = maxDistance,
    )
}
