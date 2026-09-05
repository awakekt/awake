/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.audio

import io.github.awakelab.awake.core.audio.AudioClip
import io.github.awakelab.awake.core.audio.NoOpAudioPlayer
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.transform.Transform
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
    }
}
