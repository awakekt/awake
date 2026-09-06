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
    }
}
