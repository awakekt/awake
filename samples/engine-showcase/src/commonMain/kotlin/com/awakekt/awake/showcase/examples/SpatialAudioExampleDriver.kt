/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.audio.AudioPlayer
import com.awakekt.awake.core.audio.AudioPlayerFactory
import com.awakekt.awake.core.audio.SoundSynthesizer
import com.awakekt.awake.core.audio.Waveform
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.audio.AudioListener
import com.awakekt.awake.scene.audio.AudioSource
import com.awakekt.awake.scene.audio.AudioSystem
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlin.math.cos
import kotlin.math.sin

/**
 * Demonstrates 3D spatial audio playback, attenuation, and panning relative to the camera listener.
 *
 * Spawns two distinct sound sources:
 * 1. `left-source`: Low-frequency pulsing drone located on the left beacon (-5.0, 0.5, 0.0).
 * 2. `right-source`: High-frequency bright chime located on the right beacon (5.0, 0.5, 0.0).
 *
 * Orbiting the camera or moving closer to either beacon changes perceived volume and stereo panning.
 */
internal object SpatialAudioExampleDriver {
    private var leftEmitterEntity: Entity? = null
    private var rightEmitterEntity: Entity? = null
    private var leftSource: AudioSource? = null
    private var rightSource: AudioSource? = null

    private var leftLightTransform: Transform? = null
    private var rightLightTransform: Transform? = null

    private var elapsed = 0f

    /** Master volume multiplier controlled by the showcase controls panel. */
    var masterVolume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            audioPlayer?.masterVolume = if (isMuted) 0f else field
        }

    /** Whether audio output is muted. */
    var isMuted: Boolean = false
        set(value) {
            field = value
            audioPlayer?.masterVolume = if (field) 0f else masterVolume
        }

    /** Underlying active platform audio player. */
    var audioPlayer: AudioPlayer? = null
        private set

    /** Shared audio system instance updating spatial audio sources. */
    var audioSystem: AudioSystem? = null
        private set

    /** Low-pitched drone sound clip for the left emitter. */
    val leftToneClip: AudioClip by lazy {
        SoundSynthesizer.tone(
            id = "spatial-left-drone",
            name = "Left Drone",
            frequencyHz = 220f,
            durationSeconds = 0.5f,
            waveform = Waveform.TRIANGLE,
        )
    }

    /** High-pitched chime sound clip for the right emitter. */
    val rightToneClip: AudioClip by lazy {
        SoundSynthesizer.tone(
            id = "spatial-right-chime",
            name = "Right Chime",
            frequencyHz = 660f,
            durationSeconds = 0.4f,
            waveform = Waveform.SINE,
        )
    }

    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        attach(instance, runtime.world)
    }

    fun attach(instance: Scene, world: World) {
        val player = audioPlayer ?: AudioPlayerFactory.create().also { audioPlayer = it }
        player.masterVolume = if (isMuted) 0f else masterVolume
        if (audioSystem == null) {
            audioSystem = AudioSystem(player)
        }

        // Attach AudioListener to primary camera
        world.queryEach<Camera> { entity, camera ->
            if (camera.isPrimary && world.get<AudioListener>(entity) == null) {
                world.add(entity, AudioListener())
            }
        }

        // Attach left spatial audio emitter
        val leftNode = instance.roots.find { it.name == "left-beacon" }
        if (leftNode != null) {
            val entity = leftNode.entity
            leftEmitterEntity = entity
            val source = AudioSource(
                clip = leftToneClip,
                volume = 0.9f,
                isSpatial3D = true,
                maxDistance = 25f,
                autoPlay = true,
                loop = true,
            )
            world.add(entity, source)
            leftSource = source
        }

        // Attach right spatial audio emitter
        val rightNode = instance.roots.find { it.name == "right-beacon" }
        if (rightNode != null) {
            val entity = rightNode.entity
            rightEmitterEntity = entity
            val source = AudioSource(
                clip = rightToneClip,
                volume = 0.9f,
                isSpatial3D = true,
                maxDistance = 25f,
                autoPlay = true,
                loop = true,
            )
            world.add(entity, source)
            rightSource = source
        }

        val leftLight = instance.roots.find { it.name == "left-light" }
        leftLightTransform = leftLight?.let { world.get<Transform>(it.entity) }

        val rightLight = instance.roots.find { it.name == "right-light" }
        rightLightTransform = rightLight?.let { world.get<Transform>(it.entity) }

        elapsed = 0f
    }

    fun advance(runtime: SceneAppLifecycleRuntime, delta: Float) {
        advance(runtime.world, delta)
    }

    fun advance(world: World, delta: Float) {
        elapsed += delta
        audioSystem?.update(world, delta)

        // Pulsing light beacons synchronized with sound pulses
        val pulseLeft = 1.5f + sin(elapsed * 4.0f) * 0.5f
        val pulseRight = 1.5f + cos(elapsed * 5.0f) * 0.5f
        leftLightTransform?.position?.y = pulseLeft
        rightLightTransform?.position?.y = pulseRight
    }

    fun detach(world: World) {
        leftSource?.stop()
        rightSource?.stop()

        leftEmitterEntity?.let { entity ->
            world.remove<AudioSource>(entity)
        }
        rightEmitterEntity?.let { entity ->
            world.remove<AudioSource>(entity)
        }

        leftEmitterEntity = null
        rightEmitterEntity = null
        leftSource = null
        rightSource = null
        leftLightTransform = null
        rightLightTransform = null
        elapsed = 0f
    }

    fun dispose() {
        audioPlayer?.dispose()
        audioPlayer = null
        audioSystem = null
    }
}
