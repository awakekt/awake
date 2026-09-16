/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.audio

import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.audio.NoOpAudioPlayer
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertNotNull

class AudioSystemTest {

    @Test
    fun audioSystemExecutesWithoutErrorsAndTriggersAutoPlay() {
        val world = World()
        val player = NoOpAudioPlayer()
        val system = AudioSystem(player)

        val cameraEntity = world.create()
        world.add(cameraEntity, Transform())
        world.add(cameraEntity, AudioListener())

        val clip = AudioClip(id = "beep", name = "Beep", pcmBytes = ByteArray(1000))
        val emitterEntity = world.create()
        world.add(emitterEntity, Transform())
        val source = AudioSource(clip = clip, autoPlay = true, isSpatial3D = true)
        world.add(emitterEntity, source)

        system.update(world, 1f / 60f)

        assertNotNull(source.playingHandle, "AudioSource handle should be assigned when played")
        kotlin.test.assertTrue(source.isPlaying)
    }

    @Test
    fun cleansUpFinishedPlayingHandle() {
        val world = World()
        val player = NoOpAudioPlayer()
        val system = AudioSystem(player)

        val clip = AudioClip(id = "beep", name = "Beep", pcmBytes = ByteArray(1000))
        val emitterEntity = world.create()
        world.add(emitterEntity, Transform())
        val source = AudioSource(clip = clip, autoPlay = true, isSpatial3D = false)
        world.add(emitterEntity, source)

        system.update(world, 1f / 60f)
        assertNotNull(source.playingHandle)

        // Simulate sound finishing
        source.playingHandle?.stop()

        system.update(world, 1f / 60f)
        kotlin.test.assertNull(source.playingHandle, "Playing handle should be cleared after finishing")
    }

    @Test
    fun ignoresEmptyAudioClips() {
        val world = World()
        val player = NoOpAudioPlayer()
        val system = AudioSystem(player)

        val emptyClip = AudioClip(id = "empty", name = "Empty", pcmBytes = ByteArray(0))
        val emitterEntity = world.create()
        world.add(emitterEntity, Transform())
        val source = AudioSource(clip = emptyClip, autoPlay = true, isSpatial3D = false)
        world.add(emitterEntity, source)

        system.update(world, 1f / 60f)
        kotlin.test.assertNull(source.playingHandle, "Empty clip must not trigger playback")
    }

    @Test
    fun activeSoundUpdatesSpatialVolumeAndPan() {
        val world = World()
        var updatedVolume = 0f
        var updatedPan = 0f
        val trackingHandle = object : com.awakekt.awake.core.audio.SoundHandle {
            override var isPlaying: Boolean = true
            override fun stop() { isPlaying = false }
            override fun update(volume: Float, pan: Float) {
                updatedVolume = volume
                updatedPan = pan
            }
        }
        val customPlayer = object : NoOpAudioPlayer() {
            override fun playSound3D(
                clip: AudioClip,
                emitterPos: com.awakekt.awake.core.math.Vec3f,
                listenerPos: com.awakekt.awake.core.math.Vec3f,
                listenerRight: com.awakekt.awake.core.math.Vec3f,
                maxDistance: Float,
                loop: Boolean,
            ): com.awakekt.awake.core.audio.SoundHandle = trackingHandle
        }
        val system = AudioSystem(customPlayer)

        val camera = world.create()
        world.add(camera, Transform())
        world.add(camera, AudioListener())

        val clip = AudioClip(id = "beep", name = "Beep", pcmBytes = ByteArray(1000))
        val emitter = world.create()
        val emitterTransform = Transform(position = com.awakekt.awake.core.math.Vec3f(10f, 0f, 0f))
        world.add(emitter, emitterTransform)
        val source = AudioSource(clip = clip, autoPlay = true, isSpatial3D = true)
        world.add(emitter, source)

        system.update(world, 1f / 60f)
        kotlin.test.assertSame(trackingHandle, source.playingHandle)

        // Move emitter to the left and update
        emitterTransform.position = com.awakekt.awake.core.math.Vec3f(-10f, 0f, 0f)
        system.update(world, 1f / 60f)

        kotlin.test.assertEquals(-1f, updatedPan, 0.01f)
        kotlin.test.assertTrue(updatedVolume > 0f)
    }
}
